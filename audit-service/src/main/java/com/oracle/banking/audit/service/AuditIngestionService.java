package com.oracle.banking.audit.service;

import com.oracle.banking.audit.entity.AuditConsumerFailure;
import com.oracle.banking.audit.entity.AuditLog;
import com.oracle.banking.audit.repository.AuditConsumerFailureRepository;
import com.oracle.banking.audit.repository.AuditLogRepository;
import com.oracle.banking.audit.service.AuditEventAdapter.NormalizedAuditEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIngestionService {
    private final AuditEventAdapter adapter;
    private final AuditLogRepository logs;
    private final AuditConsumerFailureRepository failures;

    public AuditIngestionService(
            AuditEventAdapter adapter, AuditLogRepository logs, AuditConsumerFailureRepository failures) {
        this.adapter = adapter;
        this.logs = logs;
        this.failures = failures;
    }

    @Transactional
    public void ingest(ConsumerRecord<String, String> record) throws Exception {
        if (logs.findByTopicAndPartitionAndOffset(record.topic(), record.partition(), record.offset()).isPresent()) return;
        NormalizedAuditEvent event = adapter.adapt(record);
        if (logs.findByEventId(event.eventId()).isPresent()) return;
        logs.save(new AuditLog(
                event.eventId(), event.producerEventId(), event.eventVersion(), event.eventType(), event.occurredAt(),
                event.actorUserId(), event.actorRole(), event.sourceService(), event.action(), event.entityType(),
                event.referenceId(), event.correlationId(), event.status(), event.severity(), event.sanitizedMetadata(),
                event.topic(), event.partition(), event.offset()));
    }

    @Transactional
    public void recordFailure(ConsumerRecord<?, ?> record, Throwable error) {
        failures.save(new AuditConsumerFailure(
                record.topic(), record.partition(), record.offset(), String.valueOf(record.key()), error));
    }
}
