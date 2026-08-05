package com.oracle.banking.card.event;

import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CardEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(CardEventPublisher.class);

    private final ApplicationEventPublisher events;
    private final RestClient authClient;
    private final String internalApiKey;

    public CardEventPublisher(
            ApplicationEventPublisher events,
            RestClient.Builder builder,
            @Value("${services.auth-service-url}") String authServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.events = events;
        this.authClient = builder.baseUrl(authServiceUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public void publish(String eventType, BankCard card, String message) {
        String recipient = recipientOrNull(card);
        events.publishEvent(new CardNotificationEvent(
                eventType,
                "CARD-" + card.getCardId(),
                card.getCustomerUserId(),
                card.getStatus().name(),
                Instant.now(),
                recipient,
                "GENERIC_NOTIFICATION",
                Map.of("message", message, "lastFourDigits", card.getLastFourDigits())));
    }

    private String recipientOrNull(BankCard card) {
        try {
            Recipient recipient = authClient.get()
                    .uri("/internal/auth/users/{userId}/notification-recipient", card.getCustomerUserId())
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(Recipient.class);
            if (recipient == null || recipient.email() == null || recipient.email().isBlank()) {
                log.warn("Card event has no notification recipient for card {}", card.getCardId());
                return null;
            }
            return recipient.email();
        } catch (RestClientException exception) {
            log.warn("Card event has no notification recipient because Auth lookup failed for card {}", card.getCardId());
            return null;
        }
    }

    private record Recipient(String userId, String username, String email) {}
}
