package com.oracle.banking.transaction.event;

import com.oracle.banking.transaction.entity.BankTransaction;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public TransactionAuditPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.transaction-reversed-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void reversed(BankTransaction transaction) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", transaction.getCustomerUserId());
        event.put("sourceService", "transaction-service");
        event.put("action", "TRANSACTION_REVERSED");
        event.put("entityType", "TRANSACTION");
        event.put("referenceId", transaction.getTransactionId());
        event.put("correlationId", transaction.getReferenceNumber());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("transactionId", transaction.getTransactionId());
        event.put("accountId", transaction.getAccountId());
        kafka.send(topic, transaction.getTransactionId(), event);
    }
}
