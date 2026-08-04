package com.oracle.banking.loan.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationRecipientClient {
    private final RestClient client;
    private final String internalApiKey;

    public NotificationRecipientClient(
            RestClient.Builder builder,
            @Value("${services.auth-service-url}") String authServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.client = builder.baseUrl(authServiceUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public String email(String userId) {
        Recipient response = client.get()
                .uri("/internal/auth/users/{userId}/notification-recipient", userId)
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                .retrieve()
                .body(Recipient.class);
        if (response == null || response.email() == null || response.email().isBlank()) {
            throw new IllegalStateException("Notification recipient is unavailable");
        }
        return response.email();
    }

    private record Recipient(String userId, String username, String email) {}
}
