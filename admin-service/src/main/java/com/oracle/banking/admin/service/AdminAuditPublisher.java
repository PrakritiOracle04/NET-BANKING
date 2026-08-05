package com.oracle.banking.admin.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public AdminAuditPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${admin.events.sensitive-action-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void globalSearch(String actorUserId, int groupCount) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", actorUserId);
        event.put("actorRole", "ADMIN");
        event.put("sourceService", "admin-service");
        event.put("action", "GLOBAL_SEARCH");
        event.put("entityType", "ADMIN_OPERATION");
        event.put("referenceId", UUID.randomUUID().toString());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("resultGroupCount", groupCount);
        kafka.send(topic, actorUserId, event);
    }
}
