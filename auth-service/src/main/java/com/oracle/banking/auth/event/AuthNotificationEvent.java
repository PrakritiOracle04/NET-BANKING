package com.oracle.banking.auth.event;

import java.util.Map;

public record AuthNotificationEvent(
        String eventType,
        String referenceNumber,
        String recipient,
        String templateName,
        Map<String, String> variables) {
}
