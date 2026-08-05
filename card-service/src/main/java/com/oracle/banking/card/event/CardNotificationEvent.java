package com.oracle.banking.card.event;

import java.time.Instant;
import java.util.Map;

public record CardNotificationEvent(
        String eventType,
        String referenceNumber,
        String actorUserId,
        String status,
        Instant occurredAt,
        String recipient,
        String templateName,
        Map<String, String> variables) {
}
