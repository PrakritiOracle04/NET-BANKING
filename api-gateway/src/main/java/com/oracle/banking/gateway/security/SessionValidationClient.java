package com.oracle.banking.gateway.security;

import com.oracle.banking.shared.constants.SecurityConstants;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SessionValidationClient {
    public enum Result {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    private final WebClient authClient;
    private final String internalApiKey;
    private final Duration timeout;

    public SessionValidationClient(
            WebClient.Builder builder,
            @Value("${services.auth-service-url}") String authServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${services.session-validation-timeout-ms:3000}") long timeoutMs) {
        this.authClient = builder.baseUrl(authServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public Mono<Result> validate(String authorizationHeader) {
        return authClient.post()
                .uri("/internal/auth/sessions/validate")
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                .header("Authorization", authorizationHeader)
                .exchangeToMono(response -> map(response.statusCode()))
                .timeout(timeout)
                .onErrorReturn(Result.UNAVAILABLE);
    }

    private Mono<Result> map(HttpStatusCode status) {
        if (status.is2xxSuccessful()) return Mono.just(Result.VALID);
        if (status.value() == 401) return Mono.just(Result.INVALID);
        return Mono.just(Result.UNAVAILABLE);
    }
}
