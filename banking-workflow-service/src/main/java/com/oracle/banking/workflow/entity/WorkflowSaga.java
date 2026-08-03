package com.oracle.banking.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "BANKING_WORKFLOWS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_WORKFLOW_OWNER_KEY_TYPE",
                columnNames = {"CUSTOMER_USER_ID", "IDEMPOTENCY_KEY", "WORKFLOW_TYPE"}),
        indexes = @Index(
                name = "IX_WORKFLOW_STATUS_UPDATED",
                columnList = "STATUS, UPDATED_AT"))
public class WorkflowSaga {
    @Id
    @Column(name = "WORKFLOW_ID", length = 36)
    private String workflowId;
    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;
    @Column(name = "IDEMPOTENCY_KEY", nullable = false, length = 120)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "WORKFLOW_TYPE", nullable = false, length = 20)
    private WorkflowType workflowType;
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private WorkflowStatus status;
    @Column(name = "REFERENCE_NUMBER", nullable = false, unique = true, length = 80)
    private String referenceNumber;
    @Column(name = "SOURCE_ACCOUNT_ID", length = 36)
    private String sourceAccountId;
    @Column(name = "DESTINATION_ACCOUNT_ID", length = 36)
    private String destinationAccountId;
    @Column(name = "DESTINATION_ACCOUNT_NUMBER", length = 30)
    private String destinationAccountNumber;
    @Column(name = "ACCOUNT_NUMBER", length = 30)
    private String accountNumber;
    @Column(name = "ACCOUNT_TYPE", length = 30)
    private String accountType;
    @Column(name = "BRANCH_IFSC", length = 11)
    private String branchIfsc;
    @Column(name = "IS_PRIMARY_ACCOUNT")
    private Boolean primaryAccount;
    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "DESCRIPTION", length = 160)
    private String description;
    @Column(name = "SOURCE_MOVEMENT_REFERENCE", length = 80)
    private String sourceMovementReference;
    @Column(name = "DESTINATION_MOVEMENT_REFERENCE", length = 80)
    private String destinationMovementReference;
    @Column(name = "DEBIT_TRANSACTION_REFERENCE", length = 80)
    private String debitTransactionReference;
    @Column(name = "CREDIT_TRANSACTION_REFERENCE", length = 80)
    private String creditTransactionReference;
    @Column(name = "DEBIT_TRANSACTION_ID", length = 36)
    private String debitTransactionId;
    @Column(name = "CREDIT_TRANSACTION_ID", length = 36)
    private String creditTransactionId;
    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected WorkflowSaga() {
    }

    public WorkflowSaga(String customerUserId, String idempotencyKey, WorkflowType workflowType, String referenceNumber,
            String sourceAccountId, String destinationAccountNumber, BigDecimal amount, String description) {
        this.workflowId = UUID.randomUUID().toString();
        this.customerUserId = customerUserId;
        this.idempotencyKey = idempotencyKey;
        this.workflowType = workflowType;
        this.referenceNumber = referenceNumber;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountNumber = destinationAccountNumber;
        this.amount = amount;
        this.description = description;
        this.status = WorkflowStatus.STARTED;
    }

    @PrePersist
    void beforeCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public String getWorkflowId() { return workflowId; }
    public String getCustomerUserId() { return customerUserId; }
    public WorkflowType getWorkflowType() { return workflowType; }
    public WorkflowStatus getStatus() { return status; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getSourceAccountId() { return sourceAccountId; }
    public String getDestinationAccountId() { return destinationAccountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public String getBranchIfsc() { return branchIfsc; }
    public boolean isPrimaryAccount() { return Boolean.TRUE.equals(primaryAccount); }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getSourceMovementReference() { return sourceMovementReference; }
    public String getDestinationMovementReference() { return destinationMovementReference; }
    public String getDebitTransactionReference() { return debitTransactionReference; }
    public String getCreditTransactionReference() { return creditTransactionReference; }
    public String getDebitTransactionId() { return debitTransactionId; }
    public String getCreditTransactionId() { return creditTransactionId; }
    public boolean hasMutation() { return sourceMovementReference != null || destinationMovementReference != null; }
    public void sourceMovementPlanned(String reference) { sourceMovementReference = reference; }
    public void destinationMovementPlanned(String accountId, String reference) { destinationAccountId = accountId; destinationMovementReference = reference; }
    public void sourceMoved(String reference) { sourceMovementReference = reference; status = WorkflowStatus.SOURCE_MOVED; }
    public void destinationMoved(String accountId, String reference) { destinationAccountId = accountId; destinationMovementReference = reference; status = WorkflowStatus.DESTINATION_MOVED; }
    public void debitTransactionRecorded(String reference, String id) { debitTransactionReference = reference; debitTransactionId = id; }
    public void creditTransactionRecorded(String reference, String id) { creditTransactionReference = reference; creditTransactionId = id; }
    public void debitTransactionPlanned(String reference) { debitTransactionReference = reference; }
    public void creditTransactionPlanned(String reference) { creditTransactionReference = reference; }
    public void transactionsRecorded() { status = WorkflowStatus.TRANSACTIONS_RECORDED; }
    public void accountOpeningRequested(String accountType, String branchIfsc) {
        this.accountType = accountType;
        this.branchIfsc = branchIfsc;
    }
    public void prerequisitesValidated() { status = WorkflowStatus.PREREQUISITES_VALIDATED; }
    public void accountCreated(String accountId, String accountNumber, boolean primaryAccount) {
        sourceAccountId = accountId;
        this.accountNumber = accountNumber;
        this.primaryAccount = primaryAccount;
        status = WorkflowStatus.ACCOUNT_CREATED;
    }
    public void retry() { status = WorkflowStatus.STARTED; failureReason = null; }
    public void complete() { status = WorkflowStatus.COMPLETED; failureReason = null; }
    public void fail(String reason) { status = WorkflowStatus.FAILED; failureReason = trim(reason); }
    public void compensating() { status = WorkflowStatus.COMPENSATING; }
    public void compensated() { status = WorkflowStatus.COMPENSATED; failureReason = null; }
    public void compensationPending(String reason) { status = WorkflowStatus.COMPENSATION_PENDING; failureReason = trim(reason); }
    private String trim(String value) { return value == null ? null : value.substring(0, Math.min(value.length(), 500)); }
}
