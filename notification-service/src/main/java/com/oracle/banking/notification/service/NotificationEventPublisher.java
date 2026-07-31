package com.oracle.banking.notification.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventPublisher {
    private final KafkaTemplate<String, Object> kafka;

    public NotificationEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public String publishTest(String recipient, Map<String, String> variables) {
        String reference = "KAFKA-TEST-" + UUID.randomUUID();
        Map<String, String> content = variables == null || variables.isEmpty()
                ? Map.of("message", "Kafka notification test completed successfully.")
                : variables;
        Map<String, Object> event = Map.of(
                "eventType", "transaction-created",
                "referenceNumber", reference,
                "occurredAt", Instant.now().toString(),
                "recipient", recipient,
                "templateName", "GENERIC_NOTIFICATION",
                "variables", content);
        kafka.send("transaction-created", reference, event);
        return reference;
    }
}
