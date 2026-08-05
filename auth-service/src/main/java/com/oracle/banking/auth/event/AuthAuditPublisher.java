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
    private final String passwordResetRequestedTopic;
    private final String passwordResetVerificationFailedTopic;
    private final String passwordResetVerifiedTopic;
    private final String passwordResetCompletedTopic;

    public AuthAuditPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.authentication-failed-topic}") String authenticationFailedTopic,
            @Value("${banking.events.session-logout-topic}") String logoutTopic,
            @Value("${banking.events.session-logout-all-topic}") String logoutAllTopic,
            @Value("${banking.events.password-reset-requested-topic}") String passwordResetRequestedTopic,
            @Value("${banking.events.password-reset-verification-failed-topic}") String passwordResetVerificationFailedTopic,
            @Value("${banking.events.password-reset-verified-topic}") String passwordResetVerifiedTopic,
            @Value("${banking.events.password-reset-completed-topic}") String passwordResetCompletedTopic) {
        this.kafka = kafka;
        this.authenticationFailedTopic = authenticationFailedTopic;
        this.logoutTopic = logoutTopic;
        this.logoutAllTopic = logoutAllTopic;
        this.passwordResetRequestedTopic = passwordResetRequestedTopic;
        this.passwordResetVerificationFailedTopic = passwordResetVerificationFailedTopic;
        this.passwordResetVerifiedTopic = passwordResetVerifiedTopic;
        this.passwordResetCompletedTopic = passwordResetCompletedTopic;
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

    public void passwordResetRequested(String actorUserId) {
        publish(passwordResetRequestedTopic, actorUserId, "PASSWORD_RESET_REQUESTED", "SUCCESS", Map.of());
    }

    public void passwordResetVerificationFailed(String actorUserId, String reasonCode) {
        publish(passwordResetVerificationFailedTopic, actorUserId, "PASSWORD_RESET_VERIFICATION_FAILED", "FAILED",
                Map.of("reasonCode", reasonCode));
    }

    public void passwordResetVerified(String actorUserId) {
        publish(passwordResetVerifiedTopic, actorUserId, "PASSWORD_RESET_VERIFIED", "SUCCESS", Map.of());
    }

    public void passwordResetCompleted(String actorUserId, int invalidatedSessions) {
        publish(passwordResetCompletedTopic, actorUserId, "PASSWORD_RESET_COMPLETED", "SUCCESS",
                Map.of("invalidatedSessions", invalidatedSessions));
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
