package com.oracle.banking.workflow.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationRecipientClient {
    private final RestClient client; private final String key;
    public NotificationRecipientClient(RestClient.Builder builder, @Value("${services.auth-service-url}") String url,
            @Value("${services.internal-api-key}") String key) { client = builder.baseUrl(url).build(); this.key = key; }
    public String email(String userId) {
        Recipient response = client.get().uri("/internal/auth/users/{userId}/notification-recipient", userId)
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, key).retrieve().body(Recipient.class);
        if (response == null || response.email() == null || response.email().isBlank()) throw new IllegalStateException("Notification recipient is unavailable");
        return response.email();
    }
    private record Recipient(String userId, String username, String email) {}
}
