package com.oracle.banking.auth.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PasswordResetNotificationClient {
    private final RestClient client;
    private final String internalApiKey;

    public PasswordResetNotificationClient(
            RestClient.Builder builder,
            @Value("${services.notification.base-url}") String notificationBaseUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.client = builder.baseUrl(notificationBaseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void sendTemplate(
            String recipient,
            String templateName,
            Map<String, String> variables,
            String sourceEvent,
            String referenceId) {
        try {
            client.post()
                    .uri("/internal/notifications/email/template")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new TemplateEmailRequest(recipient, templateName, variables, sourceEvent, referenceId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Notification service could not accept password reset email");
        }
    }

    private record TemplateEmailRequest(
            String recipient,
            String templateName,
            Map<String, String> variables,
            String sourceEvent,
            String referenceId) {}
}
