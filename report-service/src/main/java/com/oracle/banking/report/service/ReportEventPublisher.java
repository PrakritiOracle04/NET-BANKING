package com.oracle.banking.report.service;

import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

@Component
public class ReportEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ReportEventPublisher.class);
    private final KafkaTemplate<String, Object> kafka;
    private final RestClient client;
    private final String recipientUrl;
    private final String internalApiKey;

    public ReportEventPublisher(
            KafkaTemplate<String, Object> kafka, RestClient.Builder builder,
            @Value("${services.auth-service-url}") String authServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.kafka = kafka;
        this.client = builder.build();
        this.recipientUrl = authServiceUrl + "/internal/auth/users/{userId}/notification-recipient";
        this.internalApiKey = internalApiKey;
    }

    public void requestedAfterCommit(ReportJob job) { afterCommit(() -> publish("report-requested", job, false, null)); }
    public void generatedAfterCommit(ReportJob job) { afterCommit(() -> publish("report-generated", job, true, null)); }
    public void failedAfterCommit(ReportJob job) { afterCommit(() -> publish("report-failed", job, true, job.getFailureReason())); }
    public void downloaded(ReportJob job) { publish("report-downloaded", job, false, null); }
    public void expiredAfterCommit(ReportJob job) { afterCommit(() -> publish("report-expired", job, false, null)); }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    private void publish(String topic, ReportJob job, boolean notification, String failureReason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", job.getRequesterUserId());
        event.put("actorRole", job.getRequesterRole());
        event.put("sourceService", "report-service");
        event.put("action", topic.toUpperCase().replace('-', '_'));
        event.put("entityType", "REPORT");
        event.put("referenceId", job.getReportJobId());
        event.put("correlationId", job.getReportJobId());
        event.put("status", topic.contains("failed") ? "FAILED" : "SUCCESS");
        event.put("severity", topic.contains("failed") ? "WARN" : "INFO");
        event.put("reportId", job.getReportJobId());
        event.put("reportType", job.getReportType().name());
        event.put("format", job.getReportFormat().name());
        if (notification) {
            String recipient = recipient(job.getRequesterUserId());
            if (recipient != null) event.put("recipient", recipient);
            event.put("templateName", topic.equals("report-generated") ? "REPORT_READY" : "REPORT_FAILED");
            event.put("referenceNumber", job.getReportJobId());
            event.put("variables", failureReason == null
                    ? Map.of("reportId", job.getReportJobId(), "reportType", job.getReportType().name())
                    : Map.of("reportId", job.getReportJobId(), "reportType", job.getReportType().name(), "reason", bounded(failureReason)));
        }
        kafka.send(topic, job.getReportJobId(), event).whenComplete((result, error) -> {
            if (error != null) log.warn("Unable to publish {} for report {}", topic, job.getReportJobId());
        });
    }

    private String recipient(String userId) {
        try {
            Recipient value = client.get().uri(recipientUrl, userId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve().body(Recipient.class);
            return value == null ? null : value.email();
        } catch (RuntimeException ex) {
            log.warn("Unable to resolve report notification recipient for user {}", userId);
            return null;
        }
    }

    private String bounded(String value) { return value.substring(0, Math.min(value.length(), 160)); }
    private record Recipient(String email, String username) {}
}
