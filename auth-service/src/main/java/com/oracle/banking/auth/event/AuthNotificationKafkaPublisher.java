package com.oracle.banking.auth.event;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthNotificationKafkaPublisher {
    private static final Logger log = LoggerFactory.getLogger(AuthNotificationKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final String registrationTopic;
    private final String loginTopic;

    public AuthNotificationKafkaPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${banking.events.registration-success-topic}") String registrationTopic,
            @Value("${banking.events.login-alert-topic}") String loginTopic) {
        this.kafka = kafka;
        this.registrationTopic = registrationTopic;
        this.loginTopic = loginTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AuthNotificationEvent event) {
        String topic = switch (event.eventType()) {
            case "registration-success" -> registrationTopic;
            case "login-alert" -> loginTopic;
            default -> throw new IllegalArgumentException("Unsupported authentication event " + event.eventType());
        };

        Map<String, Object> payload = Map.of(
                "eventType", event.eventType(),
                "referenceNumber", event.referenceNumber(),
                "actorUserId", event.actorUserId(),
                "status", event.status(),
                "occurredAt", event.occurredAt(),
                "recipient", event.recipient(),
                "templateName", event.templateName(),
                "variables", event.variables());

        try {
            kafka.send(topic, event.referenceNumber(), payload)
                    .whenComplete((result, error) -> {
                        if (error == null) {
                            log.info(
                                    "Authentication notification published eventType={} reference={} topic={} partition={} offset={}",
                                    event.eventType(),
                                    event.referenceNumber(),
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error(
                                    "Authentication notification publish failed eventType={} reference={} topic={}",
                                    event.eventType(),
                                    event.referenceNumber(),
                                    topic,
                                    error);
                        }
                    });
        } catch (RuntimeException error) {
            log.error(
                    "Authentication notification publish failed before send eventType={} reference={} topic={}",
                    event.eventType(),
                    event.referenceNumber(),
                    topic,
                    error);
        }
    }
}
