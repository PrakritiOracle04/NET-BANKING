package com.oracle.banking.audit.event;

import com.oracle.banking.audit.service.AuditIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {
    private final AuditIngestionService ingestion;

    public AuditEventConsumer(AuditIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @KafkaListener(topics = "#{'${audit.kafka.topics}'.split(',')}", groupId = "${audit.kafka.consumer-group}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) throws Exception {
        ingestion.ingest(record);
        acknowledgment.acknowledge();
    }
}
