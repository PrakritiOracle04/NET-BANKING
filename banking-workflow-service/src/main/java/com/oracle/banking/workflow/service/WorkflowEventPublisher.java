package com.oracle.banking.workflow.service;

import com.oracle.banking.workflow.dto.WorkflowDtos.DomainEvent;
import com.oracle.banking.workflow.entity.WorkflowSaga;
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
public class WorkflowEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String transactionCreatedTopic;
    private final String accountDebitedTopic;
    private final String accountCreditedTopic;
    private final String billPaymentSuccessTopic;
    private final String billPaymentFailedTopic;
    private final String loanPaymentSuccessTopic;
    private final String loanPaymentFailedTopic;
    private final String workflowCompletedTopic;
    private final String workflowFailedTopic;
    private final String workflowCompensatedTopic;
    private final String accountOpenedTopic;

    public WorkflowEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${banking.events.transaction-created-topic}") String transactionCreatedTopic,
            @Value("${banking.events.account-debited-topic}") String accountDebitedTopic,
            @Value("${banking.events.account-credited-topic}") String accountCreditedTopic,
            @Value("${banking.events.bill-payment-success-topic}") String billPaymentSuccessTopic,
            @Value("${banking.events.bill-payment-failed-topic}") String billPaymentFailedTopic,
            @Value("${banking.events.loan-payment-success-topic}") String loanPaymentSuccessTopic,
            @Value("${banking.events.loan-payment-failed-topic}") String loanPaymentFailedTopic,
            @Value("${banking.events.workflow-completed-topic}") String workflowCompletedTopic,
            @Value("${banking.events.workflow-failed-topic}") String workflowFailedTopic,
            @Value("${banking.events.workflow-compensated-topic}") String workflowCompensatedTopic,
            @Value("${banking.events.account-opened-topic}") String accountOpenedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.transactionCreatedTopic = transactionCreatedTopic;
        this.accountDebitedTopic = accountDebitedTopic;
        this.accountCreditedTopic = accountCreditedTopic;
        this.billPaymentSuccessTopic = billPaymentSuccessTopic;
        this.billPaymentFailedTopic = billPaymentFailedTopic;
        this.loanPaymentSuccessTopic = loanPaymentSuccessTopic;
        this.loanPaymentFailedTopic = loanPaymentFailedTopic;
        this.workflowCompletedTopic = workflowCompletedTopic;
        this.workflowFailedTopic = workflowFailedTopic;
        this.workflowCompensatedTopic = workflowCompensatedTopic;
        this.accountOpenedTopic = accountOpenedTopic;
    }

    public void transactionCreated(DomainEvent event) {
        publish(transactionCreatedTopic, event);
    }

    public void accountDebited(DomainEvent event) {
        publish(accountDebitedTopic, event);
    }

    public void accountCredited(DomainEvent event) {
        publish(accountCreditedTopic, event);
    }

    public void billPaymentSucceeded(DomainEvent event) { publish(billPaymentSuccessTopic, event); }

    public void billPaymentFailed(DomainEvent event) { publish(billPaymentFailedTopic, event); }

    public void loanPaymentSucceeded(DomainEvent event) { publish(loanPaymentSuccessTopic, event); }

    public void loanPaymentFailed(DomainEvent event) { publish(loanPaymentFailedTopic, event); }

    public void workflowCompleted(WorkflowSaga saga) { publishLifecycle(workflowCompletedTopic, "WORKFLOW_COMPLETED", saga); }
    public void workflowFailed(WorkflowSaga saga) { publishLifecycle(workflowFailedTopic, "WORKFLOW_FAILED", saga); }
    public void workflowCompensated(WorkflowSaga saga) { publishLifecycle(workflowCompensatedTopic, "WORKFLOW_COMPENSATED", saga); }
    public void accountOpened(WorkflowSaga saga) { publishLifecycle(accountOpenedTopic, "ACCOUNT_OPENED", saga); }

    private void publishLifecycle(String topic, String action, WorkflowSaga saga) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventVersion", 1);
        event.put("eventType", topic);
        event.put("occurredAt", Instant.now().toString());
        event.put("actorUserId", saga.getCustomerUserId());
        event.put("sourceService", "banking-workflow-service");
        event.put("action", action);
        event.put("entityType", "ACCOUNT_OPENED".equals(action) ? "ACCOUNT" : "WORKFLOW");
        event.put("referenceId", saga.getReferenceNumber());
        event.put("correlationId", saga.getReferenceNumber());
        event.put("status", saga.getStatus().name());
        event.put("severity", saga.getStatus().name().contains("FAILED") || saga.getStatus().name().contains("PENDING") ? "WARN" : "INFO");
        event.put("workflowType", saga.getWorkflowType().name());
        event.put("accountId", saga.getSourceAccountId());
        kafkaTemplate.send(topic, saga.getReferenceNumber(), event);
    }

    private void publish(String topic, DomainEvent event) {
        if (event == null) return;
        try {
            kafkaTemplate.send(topic, event.referenceNumber(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published {} event for reference {}", topic, event.referenceNumber());
                        } else {
                            log.warn("Failed to publish {} event for reference {}", topic, event.referenceNumber());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Unable to publish {} event for reference {}", topic, event.referenceNumber());
        }
    }
}
