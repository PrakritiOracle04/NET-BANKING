package com.oracle.banking.card.event;

import java.util.Map;

public record CardNotificationEvent(
        String eventType,
        String referenceNumber,
        String recipient,
        String templateName,
        Map<String, String> variables) {
}
