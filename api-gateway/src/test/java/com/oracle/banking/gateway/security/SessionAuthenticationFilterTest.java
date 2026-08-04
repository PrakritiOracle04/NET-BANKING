package com.oracle.banking.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SessionAuthenticationFilterTest {
    @Mock
    private SessionValidationClient sessions;
    @Mock
    private GatewayFilterChain chain;

    private SessionAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new SessionAuthenticationFilter(sessions, new ObjectMapper().findAndRegisterModules());
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    }

    @Test
    void bypassesLogin() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/auth/login", null);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(sessions, never()).validate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bypassesCorsPreflight() {
        MockServerWebExchange exchange = exchange(HttpMethod.OPTIONS, "/api/accounts", null);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(sessions, never()).validate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingToken() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/accounts", null);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertError(exchange, HttpStatus.UNAUTHORIZED, "Authentication is required");
        verify(chain, never()).filter(exchange);
    }

    @Test
    void forwardsValidSession() {
        String authorization = testAuthorization();
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/accounts", authorization);
        when(sessions.validate(authorization)).thenReturn(Mono.just(SessionValidationClient.Result.VALID));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void rejectsRevokedSession() {
        String authorization = testAuthorization();
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/accounts", authorization);
        when(sessions.validate(authorization)).thenReturn(Mono.just(SessionValidationClient.Result.INVALID));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertError(exchange, HttpStatus.UNAUTHORIZED, "Session is invalid or expired");
        verify(chain, never()).filter(exchange);
    }

    @Test
    void failsClosedWhenAuthIsUnavailable() {
        String authorization = testAuthorization();
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/accounts", authorization);
        when(sessions.validate(authorization)).thenReturn(Mono.just(SessionValidationClient.Result.UNAVAILABLE));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is unavailable");
        verify(chain, never()).filter(exchange);
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.method(method, path);
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        return MockServerWebExchange.from(request.build());
    }

    private String testAuthorization() {
        return "Bearer " + UUID.randomUUID();
    }

    private void assertError(MockServerWebExchange exchange, HttpStatus status, String message) {
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"success\":false").contains(message);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
