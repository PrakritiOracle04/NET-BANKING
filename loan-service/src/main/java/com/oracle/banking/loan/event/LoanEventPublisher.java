package com.oracle.banking.loan.event;

import com.oracle.banking.loan.entity.Loan;
import com.oracle.banking.loan.entity.LoanApplication;
import com.oracle.banking.loan.service.NotificationRecipientClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoanEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);
    private final KafkaTemplate<String, Object> kafka;
    private final String loanCreatedTopic;
    private final String emiReminderTopic;
    private final String loanOverdueTopic;
    private final String loanStatusChangedTopic;
    private final String loanApplicationSubmittedTopic;
    private final String loanApplicationApprovedTopic;
    private final String loanApplicationRejectedTopic;
    private final NotificationRecipientClient recipients;

    public LoanEventPublisher(
            KafkaTemplate<String, Object> kafka,
            @Value("${loan.events.loan-created-topic}") String loanCreatedTopic,
            @Value("${loan.events.emi-reminder-topic}") String emiReminderTopic,
            @Value("${loan.events.loan-overdue-topic}") String loanOverdueTopic,
            @Value("${loan.events.loan-status-changed-topic}") String loanStatusChangedTopic,
            @Value("${loan.events.application-submitted-topic}") String loanApplicationSubmittedTopic,
            @Value("${loan.events.application-approved-topic}") String loanApplicationApprovedTopic,
            @Value("${loan.events.application-rejected-topic}") String loanApplicationRejectedTopic,
            NotificationRecipientClient recipients) {
        this.kafka = kafka;
        this.loanCreatedTopic = loanCreatedTopic;
        this.emiReminderTopic = emiReminderTopic;
        this.loanOverdueTopic = loanOverdueTopic;
        this.loanStatusChangedTopic = loanStatusChangedTopic;
        this.loanApplicationSubmittedTopic = loanApplicationSubmittedTopic;
        this.loanApplicationApprovedTopic = loanApplicationApprovedTopic;
        this.loanApplicationRejectedTopic = loanApplicationRejectedTopic;
        this.recipients = recipients;
    }

    public void loanCreated(Loan loan) {
        DomainEvent event = new DomainEvent(
                "loan-created",
                loan.getLoanNumber(),
                loan.getCustomerUserId(),
                loan.getLoanId(),
                loan.getPrincipalAmount(),
                "ACTIVE",
                Instant.now(),
                recipientOrNull(loan.getCustomerUserId(), loan.getLoanId()),
                "LOAN_CREATED",
                Map.of(
                        "loanNumber", loan.getLoanNumber(),
                        "loanType", loan.getLoanType().name(),
                        "principalAmount", loan.getPrincipalAmount().toPlainString(),
                        "emiAmount", loan.getEmiAmount().toPlainString(),
                        "maturityDate", loan.getMaturityDate().toString()));
        publish(loanCreatedTopic, event.referenceNumber(), event);
    }

    public void loanApplicationSubmitted(LoanApplication application) {
        publishApplication(loanApplicationSubmittedTopic, "loan-application-submitted", application,
                "LOAN_APPLICATION_RECEIVED", "Your loan application has been submitted for review.");
    }

    public void loanApplicationApproved(LoanApplication application) {
        publishApplication(loanApplicationApprovedTopic, "loan-application-approved", application,
                "LOAN_APPLICATION_APPROVED", "Your loan application was approved.");
    }

    public void loanApplicationRejected(LoanApplication application) {
        publishApplication(loanApplicationRejectedTopic, "loan-application-rejected", application,
                "LOAN_APPLICATION_REJECTED", "Your loan application was rejected.");
    }

    public boolean emiReminder(String reference, String customerUserId, String loanId, BigDecimal amount, LocalDate dueDate) {
        DomainEvent event = new DomainEvent(
                "emi-reminder",
                reference,
                customerUserId,
                loanId,
                amount,
                "PENDING",
                Instant.now(),
                recipientOrNull(customerUserId, loanId),
                "GENERIC_NOTIFICATION",
                Map.of(
                        "message", "Your EMI is due on " + dueDate + ".",
                        "dueDate", dueDate.toString(),
                        "amount", amount.toPlainString()));
        return publishAndAwait(emiReminderTopic, reference, event);
    }

    public boolean loanOverdue(String reference, String customerUserId, String loanId, BigDecimal amount, LocalDate dueDate) {
        DomainEvent event = new DomainEvent(
                "loan-overdue",
                reference,
                customerUserId,
                loanId,
                amount,
                "OVERDUE",
                Instant.now(),
                recipientOrNull(customerUserId, loanId),
                "GENERIC_NOTIFICATION",
                Map.of(
                        "message", "Your EMI due on " + dueDate + " is overdue.",
                        "dueDate", dueDate.toString(),
                        "amount", amount.toPlainString()));
        return publishAndAwait(loanOverdueTopic, reference, event);
    }

    public void loanStatusChanged(Loan loan) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", loanStatusChangedTopic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", loan.getCustomerUserId());
        event.put("sourceService", "loan-service");
        event.put("action", "LOAN_STATUS_CHANGED");
        event.put("entityType", "LOAN");
        event.put("referenceId", loan.getLoanId());
        event.put("status", "SUCCESS");
        event.put("severity", "INFO");
        event.put("loanId", loan.getLoanId());
        event.put("loanType", loan.getLoanType().name());
        event.put("loanStatus", loan.getStatus().name());
        kafka.send(loanStatusChangedTopic, loan.getLoanId(), event);
    }

    private String recipientOrNull(String customerUserId, String loanId) {
        try {
            return recipients.email(customerUserId);
        } catch (RuntimeException exception) {
            log.warn("Loan event has no notification recipient for loan {}", loanId);
            return null;
        }
    }

    private void publishApplication(String topic, String eventType, LoanApplication application, String templateName, String message) {
        DomainEvent event = new DomainEvent(
                eventType,
                "LOANAPP-" + application.getApplicationId(),
                application.getCustomerUserId(),
                application.getIssuedLoanId(),
                application.getRequestedAmount(),
                application.getStatus().name(),
                Instant.now(),
                recipientOrNull(application.getCustomerUserId(), application.getApplicationId()),
                templateName,
                Map.of(
                        "message", message,
                        "applicationId", application.getApplicationId(),
                        "loanType", application.getLoanType().name(),
                        "requestedAmount", application.getRequestedAmount().toPlainString()));
        publish(topic, event.referenceNumber(), event);
    }

    private void publish(String topic, String key, DomainEvent event) {
        try {
            kafka.send(topic, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) log.info("Published {} event for reference {}", topic, key);
                        else log.warn("Failed to publish {} event for reference {}", topic, key);
                    });
        } catch (Exception ex) {
            log.warn("Unable to publish {} event for reference {}", topic, key);
        }
    }

    private boolean publishAndAwait(String topic, String key, DomainEvent event) {
        try {
            kafka.send(topic, key, event).get(3, TimeUnit.SECONDS);
            log.info("Published {} event for reference {}", topic, key);
            return true;
        } catch (Exception ex) {
            log.warn("Unable to publish {} event for reference {}", topic, key);
            return false;
        }
    }

    public record DomainEvent(
            String eventType,
            String referenceNumber,
            String customerUserId,
            String loanId,
            BigDecimal amount,
            String status,
            Instant occurredAt,
            String recipient,
            String templateName,
            Map<String, String> variables) {}
}
