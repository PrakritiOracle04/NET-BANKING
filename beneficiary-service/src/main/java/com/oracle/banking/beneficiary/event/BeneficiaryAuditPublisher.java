package com.oracle.banking.beneficiary.event;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BeneficiaryAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final Map<String, String> topics;

    public BeneficiaryAuditPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.beneficiary-created-topic}") String created,
            @Value("${banking.events.beneficiary-updated-topic}") String updated,
            @Value("${banking.events.beneficiary-deleted-topic}") String deleted,
            @Value("${banking.events.beneficiary-status-changed-topic}") String statusChanged) {
        this.kafka = kafka;
        this.topics = Map.of("CREATED", created, "UPDATED", updated, "DELETED", deleted, "STATUS_CHANGED", statusChanged);
    }

    public void publish(String operation, Beneficiary beneficiary) {
        String topic = topics.get(operation);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", beneficiary.getCustomerUserId());
        event.put("sourceService", "beneficiary-service");
        event.put("action", "BENEFICIARY_" + operation);
        event.put("entityType", "BENEFICIARY");
        event.put("referenceId", beneficiary.getBeneficiaryId());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("beneficiaryId", beneficiary.getBeneficiaryId());
        event.put("beneficiaryStatus", beneficiary.getStatus().name());
        kafka.send(topic, beneficiary.getBeneficiaryId(), event);
    }
}
