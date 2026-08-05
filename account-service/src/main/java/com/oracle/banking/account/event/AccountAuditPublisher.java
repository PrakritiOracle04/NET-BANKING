package com.oracle.banking.account.event;

import com.oracle.banking.account.entity.Account;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String topic;

    public AccountAuditPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.account-status-changed-topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void statusChanged(Account account) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", account.getCustomerUserId());
        event.put("sourceService", "account-service");
        event.put("action", "ACCOUNT_STATUS_CHANGED");
        event.put("entityType", "ACCOUNT");
        event.put("referenceId", account.getAccountId());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("accountId", account.getAccountId());
        event.put("accountStatus", account.getStatus().name());
        kafka.send(topic, account.getAccountId(), event);
    }
}
