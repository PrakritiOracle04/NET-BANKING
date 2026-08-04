package com.oracle.banking.workflow.service;

import com.oracle.banking.workflow.dto.WorkflowDtos.DomainEvent;
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

    public WorkflowEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${banking.events.transaction-created-topic}") String transactionCreatedTopic,
            @Value("${banking.events.account-debited-topic}") String accountDebitedTopic,
            @Value("${banking.events.account-credited-topic}") String accountCreditedTopic,
            @Value("${banking.events.bill-payment-success-topic}") String billPaymentSuccessTopic,
            @Value("${banking.events.bill-payment-failed-topic}") String billPaymentFailedTopic,
            @Value("${banking.events.loan-payment-success-topic}") String loanPaymentSuccessTopic,
            @Value("${banking.events.loan-payment-failed-topic}") String loanPaymentFailedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.transactionCreatedTopic = transactionCreatedTopic;
        this.accountDebitedTopic = accountDebitedTopic;
        this.accountCreditedTopic = accountCreditedTopic;
        this.billPaymentSuccessTopic = billPaymentSuccessTopic;
        this.billPaymentFailedTopic = billPaymentFailedTopic;
        this.loanPaymentSuccessTopic = loanPaymentSuccessTopic;
        this.loanPaymentFailedTopic = loanPaymentFailedTopic;
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
