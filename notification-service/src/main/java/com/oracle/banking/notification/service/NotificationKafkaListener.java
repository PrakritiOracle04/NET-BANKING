package com.oracle.banking.notification.service;

import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    private final NotificationService service;

    public NotificationKafkaListener(NotificationService service) {
        this.service = service;
    }

    @KafkaListener(topics = {
            "registration-success",
            "login-alert",
            "transaction-created",
            "bill-payment-created",
            "bill-payment-success",
            "bill-payment-failed",
            "loan-created",
            "loan-payment-success",
            "loan-payment-failed",
            "emi-reminder",
            "loan-overdue",
            "schedule-triggered",
            "schedule-completed",
            "schedule-failed",
            "card-issued",
            "card-activated",
            "card-blocked",
            "card-unblocked",
            "card-limit-updated",
            "password-reset-request",
            "security-alert"
    })
    public void consume(Map<String, Object> event) {
        String eventType = String.valueOf(
                event.getOrDefault("eventType", "generic-notification"));
        Object recipient = event.get("recipient");

        if (recipient == null) {
            log.warn(
                    "Received {} event without recipient; notification was not sent",
                    eventType);
            return;
        }

        Map<String, String> variables = extractVariables(event, eventType);
        String templateName = String.valueOf(
                event.getOrDefault("templateName", "GENERIC_NOTIFICATION"));
        String referenceNumber = String.valueOf(
                event.getOrDefault("referenceNumber", ""));

        EmailRequest request = new EmailRequest(
                String.valueOf(recipient),
                templateName,
                variables,
                eventType,
                referenceNumber);
        service.send(request);

        log.info("Processed Kafka notification event {}", eventType);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractVariables(
            Map<String, Object> event,
            String eventType) {
        Object variables = event.get("variables");
        if (variables instanceof Map<?, ?> rawVariables) {
            return (Map<String, String>) (Map<?, ?>) rawVariables;
        }

        return Map.of(
                "message",
                String.valueOf(event.getOrDefault("message", eventType)));
    }
}
