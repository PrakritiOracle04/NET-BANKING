package com.oracle.banking.scheduler.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NotificationRecipientClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationRecipientClient.class);

    private final RestClient client;
    private final String internalApiKey;

    public NotificationRecipientClient(
            RestClient.Builder builder,
            @Value("${services.auth-service-url}") String authServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.client = builder.baseUrl(authServiceUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public String emailOrNull(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            Recipient response = client.get()
                    .uri("/internal/auth/users/{userId}/notification-recipient", userId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(Recipient.class);
            return response == null || response.email() == null || response.email().isBlank()
                    ? null
                    : response.email();
        } catch (RestClientException exception) {
            log.warn("Unable to resolve notification recipient for schedule owner {}", userId);
            return null;
        }
    }

    private record Recipient(String userId, String username, String email) {}
}
