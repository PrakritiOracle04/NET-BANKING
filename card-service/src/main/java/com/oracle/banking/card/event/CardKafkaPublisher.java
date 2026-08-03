package com.oracle.banking.card.event;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CardKafkaPublisher {
    private static final Logger log = LoggerFactory.getLogger(CardKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final Map<String, String> topics;

    public CardKafkaPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${card.events.issued-topic}") String issued,
            @Value("${card.events.activated-topic}") String activated,
            @Value("${card.events.blocked-topic}") String blocked,
            @Value("${card.events.unblocked-topic}") String unblocked,
            @Value("${card.events.limit-updated-topic}") String limitUpdated) {
        this.kafka = kafka;
        this.topics = Map.of(
                "card-issued", issued,
                "card-activated", activated,
                "card-blocked", blocked,
                "card-unblocked", unblocked,
                "card-limit-updated", limitUpdated);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(CardNotificationEvent event) {
        String topic = topics.get(event.eventType());
        if (topic == null) {
            log.warn("Unsupported card notification event {}", event.eventType());
            return;
        }
        Map<String, Object> payload = Map.of(
                "eventType", event.eventType(),
                "referenceNumber", event.referenceNumber(),
                "recipient", event.recipient(),
                "templateName", event.templateName(),
                "variables", event.variables());
        try {
            kafka.send(topic, event.referenceNumber(), payload).whenComplete((result, error) -> {
                if (error == null) log.info("Published card event {} for {}", event.eventType(), event.referenceNumber());
                else log.warn("Failed to publish card event {} for {}", event.eventType(), event.referenceNumber());
            });
        } catch (RuntimeException exception) {
            log.warn("Unable to publish card event {} for {}", event.eventType(), event.referenceNumber());
        }
    }
}
