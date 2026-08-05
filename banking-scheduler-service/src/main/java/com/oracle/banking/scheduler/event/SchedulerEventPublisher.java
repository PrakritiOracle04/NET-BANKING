package com.oracle.banking.scheduler.event;

import com.oracle.banking.scheduler.dto.SchedulerDtos.DomainEvent;
import com.oracle.banking.scheduler.entity.BankingSchedule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
    private final Map<String, String> lifecycleTopics;

    public SchedulerEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${banking.events.schedule-triggered-topic}") String triggeredTopic,
            @Value("${banking.events.schedule-completed-topic}") String completedTopic,
            @Value("${banking.events.schedule-failed-topic}") String failedTopic,
            @Value("${banking.events.schedule-created-topic}") String createdTopic,
            @Value("${banking.events.schedule-updated-topic}") String updatedTopic,
            @Value("${banking.events.schedule-paused-topic}") String pausedTopic,
            @Value("${banking.events.schedule-resumed-topic}") String resumedTopic,
            @Value("${banking.events.schedule-cancelled-topic}") String cancelledTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.triggeredTopic = triggeredTopic;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
        this.lifecycleTopics = Map.of(
                "CREATED", createdTopic, "UPDATED", updatedTopic, "PAUSED", pausedTopic,
                "RESUMED", resumedTopic, "CANCELLED", cancelledTopic);
    }

    public void triggered(DomainEvent event) { publish(triggeredTopic, event); }
    public void completed(DomainEvent event) { publish(completedTopic, event); }
    public void failed(DomainEvent event) { publish(failedTopic, event); }

    public void lifecycle(String operation, BankingSchedule schedule) {
        String topic = lifecycleTopics.get(operation);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", schedule.getCustomerUserId());
        event.put("sourceService", "banking-scheduler-service");
        event.put("action", "SCHEDULE_" + operation);
        event.put("entityType", "SCHEDULE");
        event.put("referenceId", schedule.getScheduleId());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("scheduleId", schedule.getScheduleId());
        event.put("operationType", schedule.getOperationType().name());
        kafkaTemplate.send(topic, schedule.getScheduleId(), event);
    }

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
