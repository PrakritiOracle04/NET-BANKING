package com.oracle.banking.notification.service;

import com.oracle.banking.notification.dto.NotificationDtos.EmailRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportNotificationKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(ReportNotificationKafkaListener.class);
    private final NotificationService service;

    public ReportNotificationKafkaListener(NotificationService service) {
        this.service = service;
    }

    @KafkaListener(topics = {"report-generated", "report-failed"})
    public void consume(Map<String, Object> event) {
        Object recipient = event.get("recipient");
        if (recipient == null) {
            log.warn("Report event {} has no current notification recipient", event.get("eventType"));
            return;
        }
        service.send(new EmailRequest(
                String.valueOf(recipient),
                String.valueOf(event.get("templateName")),
                variables(event),
                String.valueOf(event.get("eventType")),
                String.valueOf(event.get("referenceNumber"))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> variables(Map<String, Object> event) {
        Object variables = event.get("variables");
        return variables instanceof Map<?, ?> values
                ? (Map<String, String>) (Map<?, ?>) values
                : Map.of("reportId", String.valueOf(event.get("referenceNumber")));
    }
}
