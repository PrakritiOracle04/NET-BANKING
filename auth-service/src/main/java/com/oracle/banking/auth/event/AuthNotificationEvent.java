package com.oracle.banking.auth.event;

import java.time.Instant;
import java.util.Map;

public record AuthNotificationEvent(
        String eventType,
        String referenceNumber,
        String actorUserId,
        String status,
        Instant occurredAt,
        String recipient,
        String templateName,
        Map<String, String> variables) {
}
