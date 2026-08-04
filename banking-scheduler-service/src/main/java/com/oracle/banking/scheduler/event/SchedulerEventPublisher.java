package com.oracle.banking.scheduler.event;

import com.oracle.banking.scheduler.dto.SchedulerDtos.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchedulerEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SchedulerEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String triggeredTopic;
    private final String completedTopic;
    private final String failedTopic;

    public SchedulerEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${banking.events.schedule-triggered-topic}") String triggeredTopic,
            @Value("${banking.events.schedule-completed-topic}") String completedTopic,
            @Value("${banking.events.schedule-failed-topic}") String failedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.triggeredTopic = triggeredTopic;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
    }

    public void triggered(DomainEvent event) { publish(triggeredTopic, event); }
    public void completed(DomainEvent event) { publish(completedTopic, event); }
    public void failed(DomainEvent event) { publish(failedTopic, event); }

    private void publish(String topic, DomainEvent event) {
        if (event == null) return;
        try {
            kafkaTemplate.send(topic, event.referenceNumber(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published {} event for schedule {}", topic, event.scheduleId());
                        } else {
                            log.warn("Failed to publish {} event for schedule {}", topic, event.scheduleId());
                        }
                    });
        } catch (Exception exception) {
            log.warn("Unable to publish {} event for schedule {}", topic, event.scheduleId());
        }
    }
}
