package com.oracle.banking.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Check(constraints = "AMOUNT > 0")
@Table(
        name = "BILL_PAYMENTS",
        indexes = {
            @Index(name = "IX_BILL_PAYMENT_OWNER_DATE", columnList = "CUSTOMER_USER_ID, CREATED_AT DESC"),
            @Index(name = "IX_BILL_PAYMENT_ACCOUNT_DATE", columnList = "SOURCE_ACCOUNT_ID, CREATED_AT DESC"),
            @Index(name = "IX_BILL_PAYMENT_STATUS_UPDATED", columnList = "STATUS, UPDATED_AT")
        })
public class BillPayment {
    @Id
    @Column(name = "BILL_PAYMENT_ID", length = 36)
    private String billPaymentId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CUSTOMER_BILLER_ID", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_PAYMENT_CUSTOMER_BILLER"))
    private CustomerBiller customerBiller;

    @Column(name = "BILLER_ID", nullable = false, length = 36)
    private String billerId;

    @Column(name = "BILLER_NAME", nullable = false, length = 120)
    private String billerName;

    @Column(name = "CONSUMER_REFERENCE", nullable = false, length = 80)
    private String consumerReference;

    @Column(name = "SOURCE_ACCOUNT_ID", nullable = false, length = 36)
    private String sourceAccountId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private BillPaymentStatus status;

    @Column(name = "WORKFLOW_REFERENCE", nullable = false, unique = true, length = 80)
    private String workflowReference;

    @Column(name = "TRANSACTION_ID", length = 36)
    private String transactionId;

    @Column(name = "TRANSACTION_REFERENCE", length = 80)
    private String transactionReference;

    @Column(name = "DESCRIPTION", length = 160)
    private String description;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @PrePersist
    void beforeCreate() {
        if (billPaymentId == null) billPaymentId = UUID.randomUUID().toString();
        if (status == null) status = BillPaymentStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public void complete(String transactionId, String transactionReference) {
        this.transactionId = transactionId;
        this.transactionReference = transactionReference;
        status = BillPaymentStatus.SUCCESS;
        failureReason = null;
        completedAt = Instant.now();
    }

    public void fail(String reason) {
        status = BillPaymentStatus.FAILED;
        failureReason = trim(reason);
        completedAt = Instant.now();
    }

    public void cancel(String reason) {
        status = BillPaymentStatus.CANCELLED;
        failureReason = trim(reason);
        completedAt = Instant.now();
    }

    private String trim(String value) {
        return value == null ? null : value.substring(0, Math.min(500, value.length()));
    }

    public String getBillPaymentId() { return billPaymentId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public CustomerBiller getCustomerBiller() { return customerBiller; }
    public void setCustomerBiller(CustomerBiller customerBiller) { this.customerBiller = customerBiller; }
    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }
    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }
    public String getConsumerReference() { return consumerReference; }
    public void setConsumerReference(String consumerReference) { this.consumerReference = consumerReference; }
    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BillPaymentStatus getStatus() { return status; }
    public String getWorkflowReference() { return workflowReference; }
    public void setWorkflowReference(String workflowReference) { this.workflowReference = workflowReference; }
    public String getTransactionId() { return transactionId; }
    public String getTransactionReference() { return transactionReference; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
