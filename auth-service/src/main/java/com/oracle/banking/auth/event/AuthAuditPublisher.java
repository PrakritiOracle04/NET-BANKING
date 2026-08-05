package com.oracle.banking.auth.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String authenticationFailedTopic;
    private final String logoutTopic;
    private final String logoutAllTopic;

    public AuthAuditPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.authentication-failed-topic}") String authenticationFailedTopic,
            @Value("${banking.events.session-logout-topic}") String logoutTopic,
            @Value("${banking.events.session-logout-all-topic}") String logoutAllTopic) {
        this.kafka = kafka;
        this.authenticationFailedTopic = authenticationFailedTopic;
        this.logoutTopic = logoutTopic;
        this.logoutAllTopic = logoutAllTopic;
    }

    public void authenticationFailed(String actorUserId) {
        publish(authenticationFailedTopic, actorUserId, "AUTHENTICATION_FAILED", "FAILED", Map.of("reasonCode", "INVALID_CREDENTIALS"));
    }

    public void logout(String actorUserId) {
        publish(logoutTopic, actorUserId, "SESSION_LOGOUT", "SUCCESS", Map.of());
    }

    public void logoutAll(String actorUserId, int invalidatedSessions) {
        publish(logoutAllTopic, actorUserId, "SESSION_LOGOUT_ALL", "SUCCESS", Map.of("invalidatedSessions", invalidatedSessions));
    }

    private void publish(String topic, String actorUserId, String action, String status, Map<String, Object> metadata) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", actorUserId);
        event.put("actorRole", "CUSTOMER");
        event.put("sourceService", "auth-service");
        event.put("action", action);
        event.put("entityType", "SESSION");
        event.put("referenceId", UUID.randomUUID().toString());
        event.put("status", status);
        event.put("severity", "FAILED".equals(status) ? "WARN" : "INFO");
        event.putAll(metadata);
        kafka.send(topic, actorUserId == null ? "anonymous" : actorUserId, event);
    }
}
