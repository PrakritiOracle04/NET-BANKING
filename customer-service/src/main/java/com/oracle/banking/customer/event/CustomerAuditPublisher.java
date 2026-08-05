package com.oracle.banking.customer.event;

import com.oracle.banking.customer.entity.CustomerKyc;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CustomerAuditPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final String submittedTopic;
    private final String statusTopic;

    public CustomerAuditPublisher(KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.kyc-submitted-topic}") String submittedTopic,
            @Value("${banking.events.kyc-status-changed-topic}") String statusTopic) {
        this.kafka = kafka;
        this.submittedTopic = submittedTopic;
        this.statusTopic = statusTopic;
    }

    public void submitted(CustomerKyc kyc) { publish(submittedTopic, "KYC_SUBMITTED", kyc); }
    public void statusChanged(CustomerKyc kyc) { publish(statusTopic, "KYC_STATUS_CHANGED", kyc); }

    private void publish(String topic, String action, CustomerKyc kyc) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", kyc.getUserId());
        event.put("sourceService", "customer-service");
        event.put("action", action);
        event.put("entityType", "KYC");
        event.put("referenceId", kyc.getKycId());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("kycStatus", kyc.getStatus().name());
        kafka.send(topic, kyc.getKycId(), event);
    }
}
