package com.oracle.banking.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class AuditEventAdapterTest {
    private final AuditEventAdapter adapter = new AuditEventAdapter(new ObjectMapper(), 2_000);

    @Test
    void adaptsLegacyEventAndDropsNotificationAndSensitiveFields() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "loan-created", 2, 41L, "loan-1",
                """
                {
                  "customerUserId":"user-1",
                  "loanId":"loan-1",
                  "loanType":"HOME",
                  "recipient":"person@example.com",
                  "accountNumber":"123456789012",
                  "occurredAt":"2026-08-05T06:00:00Z"
                }
                """);

        var event = adapter.adapt(record);

        assertThat(event.eventId()).isEqualTo("loan-created:2:41");
        assertThat(event.actorUserId()).isEqualTo("user-1");
        assertThat(event.action()).isEqualTo("LOAN_CREATED");
        assertThat(event.sanitizedMetadata()).contains("loanId", "loanType");
        assertThat(event.sanitizedMetadata()).doesNotContain("recipient", "example.com", "accountNumber", "123456789012");
    }

    @Test
    void retainsCanonicalProducerEventId() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "report-generated", 0, 9L, "report-1",
                """
                {
                  "eventId":"event-123",
                  "eventVersion":1,
                  "eventType":"report-generated",
                  "actorUserId":"admin-1",
                  "referenceId":"report-1",
                  "status":"SUCCESS"
                }
                """);

        var event = adapter.adapt(record);

        assertThat(event.eventId()).isEqualTo("event-123");
        assertThat(event.producerEventId()).isEqualTo("event-123");
        assertThat(event.sourceService()).isEqualTo("report-service");
    }
}
