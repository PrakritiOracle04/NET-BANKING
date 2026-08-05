package com.oracle.banking.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuditEventAdapter {
    private static final Set<String> BLOCKED_KEYS = Set.of(
            "recipient", "email", "password", "token", "jwt", "otp", "secret", "apikey",
            "aadhaar", "aadhar", "pan", "cardnumber", "accountnumber", "message", "body");
    private static final Set<String> METADATA_KEYS = Set.of(
            "accountId", "sourceAccountId", "destinationAccountId", "cardId", "loanId", "loanType",
            "scheduleId", "executionId", "billPaymentId", "transactionId", "workflowType", "operationType",
            "reportId", "reportType", "format", "amount", "reasonCode");

    private final ObjectMapper mapper;
    private final int metadataMaxLength;

    public AuditEventAdapter(ObjectMapper mapper, @Value("${audit.consumer.metadata-max-length}") int metadataMaxLength) {
        this.mapper = mapper;
        this.metadataMaxLength = metadataMaxLength;
    }

    public NormalizedAuditEvent adapt(ConsumerRecord<String, String> record) throws JsonProcessingException {
        JsonNode root = mapper.readTree(record.value());
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Audit event must be a JSON object");
        TopicContract contract = TopicContract.forTopic(record.topic());
        String producerEventId = text(root, "eventId");
        String eventId = producerEventId != null
                ? producerEventId
                : record.topic() + ":" + record.partition() + ":" + record.offset();
        String eventType = first(root, "eventType", contract.eventType);
        String action = first(root, "action", contract.action);
        String entityType = first(root, "entityType", contract.entityType);
        String reference = firstPresent(root,
                "referenceId", "referenceNumber", "workflowReference", "loanId", "cardId", "scheduleId",
                "transactionId", "billPaymentId", "userId");
        String actor = firstPresent(root, "actorUserId", "customerUserId", "userId");
        String correlation = firstPresent(root, "correlationId", "workflowReference", "referenceNumber");
        Instant occurredAt = instant(root.path("occurredAt").asText(null));
        ObjectNode metadata = mapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalized = field.getKey().replace("_", "").replace("-", "").toLowerCase();
            if (METADATA_KEYS.contains(field.getKey()) && !BLOCKED_KEYS.contains(normalized) && field.getValue().isValueNode()) {
                metadata.set(field.getKey(), field.getValue());
            }
        }
        String metadataJson = mapper.writeValueAsString(metadata);
        if (metadataJson.length() > metadataMaxLength) metadataJson = "{\"truncated\":true}";
        return new NormalizedAuditEvent(
                eventId, producerEventId, root.path("eventVersion").asInt(1), eventType,
                occurredAt == null ? Instant.now() : occurredAt, actor, text(root, "actorRole"),
                first(root, "sourceService", contract.sourceService), action, entityType, reference,
                correlation, first(root, "status", contract.status), first(root, "severity", contract.severity),
                metadataJson, record.topic(), record.partition(), record.offset());
    }

    private static String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private static String first(JsonNode root, String name, String fallback) {
        String value = text(root, name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstPresent(JsonNode root, String... names) {
        for (String name : names) {
            String value = text(root, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static Instant instant(String value) {
        try { return value == null ? null : Instant.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    public record NormalizedAuditEvent(
            String eventId, String producerEventId, int eventVersion, String eventType, Instant occurredAt,
            String actorUserId, String actorRole, String sourceService, String action, String entityType,
            String referenceId, String correlationId, String status, String severity, String sanitizedMetadata,
            String topic, int partition, long offset) {}

    private record TopicContract(
            String eventType, String sourceService, String action, String entityType, String status, String severity) {
        private static TopicContract forTopic(String topic) {
            return switch (topic) {
                case "registration-success" -> value(topic, "auth-service", "USER_REGISTERED", "USER", "SUCCESS");
                case "login-alert" -> value(topic, "auth-service", "LOGIN_SUCCEEDED", "SESSION", "SUCCESS");
                case "authentication-failed" -> value(topic, "auth-service", "AUTHENTICATION_FAILED", "SESSION", "FAILED");
                case "session-logout", "session-logout-all" -> value(topic, "auth-service", topic.toUpperCase().replace('-', '_'), "SESSION", "SUCCESS");
                case "card-issued", "card-activated", "card-blocked", "card-unblocked", "card-limit-updated" ->
                        value(topic, "card-service", topic.toUpperCase().replace('-', '_'), "CARD", "SUCCESS");
                case "loan-created", "emi-reminder", "loan-overdue", "loan-status-changed" ->
                        value(topic, "loan-service", topic.toUpperCase().replace('-', '_'), "LOAN", "SUCCESS");
                case "schedule-triggered", "schedule-completed", "schedule-failed", "schedule-created",
                        "schedule-updated", "schedule-paused", "schedule-resumed", "schedule-cancelled" ->
                        value(topic, "banking-scheduler-service", topic.toUpperCase().replace('-', '_'), "SCHEDULE", status(topic));
                case "beneficiary-created", "beneficiary-updated", "beneficiary-deleted", "beneficiary-status-changed" ->
                        value(topic, "beneficiary-service", topic.toUpperCase().replace('-', '_'), "BENEFICIARY", "SUCCESS");
                case "kyc-submitted", "kyc-status-changed" ->
                        value(topic, "customer-service", topic.toUpperCase().replace('-', '_'), "KYC", "SUCCESS");
                case "transaction-reversed" -> value(topic, "transaction-service", "TRANSACTION_REVERSED", "TRANSACTION", "SUCCESS");
                case "account-status-changed" -> value(topic, "account-service", "ACCOUNT_STATUS_CHANGED", "ACCOUNT", "SUCCESS");
                case "report-requested", "report-generated", "report-failed", "report-downloaded", "report-expired" ->
                        value(topic, "report-service", topic.toUpperCase().replace('-', '_'), "REPORT", status(topic));
                case "admin-sensitive-action" -> value(topic, "admin-service", "ADMIN_SENSITIVE_ACTION", "ADMIN_OPERATION", "SUCCESS");
                default -> workflow(topic);
            };
        }

        private static TopicContract workflow(String topic) {
            String entity = topic.contains("bill-payment") ? "BILL_PAYMENT"
                    : topic.contains("loan-payment") ? "LOAN_REPAYMENT"
                    : topic.contains("account") ? "ACCOUNT"
                    : topic.contains("transaction") ? "TRANSACTION" : "WORKFLOW";
            return value(topic, "banking-workflow-service", topic.toUpperCase().replace('-', '_'), entity, status(topic));
        }

        private static String status(String topic) {
            return topic.contains("failed") ? "FAILED" : "SUCCESS";
        }

        private static TopicContract value(String topic, String source, String action, String entity, String status) {
            return new TopicContract(topic, source, action, entity, status, topic.contains("failed") ? "WARN" : "INFO");
        }
    }
}
