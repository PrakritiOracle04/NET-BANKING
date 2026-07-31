package com.oracle.banking.notification.service;

import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import com.oracle.banking.notification.dto.NotificationDtos.EmailResponse;
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
            "card-blocked",
            "card-unblocked",
            "password-reset-request",
            "security-alert"
    })
    public void consume(Map<String, Object> event) {
        String type = String.valueOf(event.getOrDefault("eventType", "generic-notification"));
        Object recipient = event.get("recipient");
        if (recipient == null) {
            log.warn("Kafka email skipped eventType={} reason=missing-recipient", type);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> variables = event.get("variables") instanceof Map<?, ?> raw
                ? (Map<String, String>) (Map<?, ?>) raw
                : Map.of("message", String.valueOf(event.getOrDefault("message", type)));

        String template = String.valueOf(event.getOrDefault("templateName", "GENERIC_NOTIFICATION"));
        String reference = String.valueOf(event.getOrDefault("referenceNumber", ""));
        EmailResponse response = service.send(new EmailRequest(
                String.valueOf(recipient),
                template,
                variables,
                type,
                reference));

        log.info(
                "Kafka email processed eventType={} reference={} notificationId={} status={}",
                type,
                reference,
                response.notificationId(),
                response.status());
    }
}
