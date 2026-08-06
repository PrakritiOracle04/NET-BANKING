package com.oracle.banking.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SessionAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/verify",
            "/api/auth/password-reset/confirm");

    private final SessionValidationClient sessions;
    private final ObjectMapper objectMapper;

    public SessionAuthenticationFilter(SessionValidationClient sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")
                || PUBLIC_API_PATHS.contains(path)
                || exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.startsWith(SecurityConstants.BEARER_PREFIX)
                || authorization.substring(SecurityConstants.BEARER_PREFIX.length()).isBlank()) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        return sessions.validate(authorization).flatMap(result -> switch (result) {
            case VALID -> chain.filter(exchange);
            case INVALID -> error(exchange, HttpStatus.UNAUTHORIZED, "Session is invalid or expired");
            case UNAVAILABLE -> error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is unavailable");
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ErrorResponse.of(message, exchange.getRequest().getPath().value()));
        } catch (JsonProcessingException exception) {
            bytes = ("{\"success\":false,\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
