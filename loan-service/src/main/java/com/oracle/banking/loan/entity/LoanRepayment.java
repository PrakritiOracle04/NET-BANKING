package com.oracle.banking.loan.entity;

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
        name = "LOAN_REPAYMENTS",
        indexes = {
            @Index(name = "IX_REPAYMENT_LOAN_DATE", columnList = "LOAN_ID, CREATED_AT DESC"),
            @Index(name = "IX_REPAYMENT_OWNER_DATE", columnList = "CUSTOMER_USER_ID, CREATED_AT DESC"),
            @Index(name = "IX_REPAYMENT_STATUS_UPDATED", columnList = "STATUS, UPDATED_AT")
        })
public class LoanRepayment {
    @Id
    @Column(name = "LOAN_REPAYMENT_ID", length = 36)
    private String loanRepaymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LOAN_ID", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_REPAYMENT_LOAN"))
    private Loan loan;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "SOURCE_ACCOUNT_ID", nullable = false, length = 36)
    private String sourceAccountId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "WORKFLOW_REFERENCE", nullable = false, unique = true, length = 80)
    private String workflowReference;

    @Column(name = "TRANSACTION_ID", length = 36)
    private String transactionId;

    @Column(name = "TRANSACTION_REFERENCE", length = 80)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanRepaymentStatus status;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Column(name = "PRINCIPAL_APPLIED", precision = 19, scale = 2)
    private BigDecimal principalApplied;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Column(name = "REVERSED_AT")
    private Instant reversedAt;

    protected LoanRepayment() {
    }

    public LoanRepayment(Loan loan, String customerUserId, String sourceAccountId, BigDecimal amount, String workflowReference) {
        this.loanRepaymentId = UUID.randomUUID().toString();
        this.loan = loan;
        this.customerUserId = customerUserId;
        this.sourceAccountId = sourceAccountId;
        this.amount = amount;
        this.workflowReference = workflowReference;
        this.status = LoanRepaymentStatus.PENDING;
        this.principalApplied = BigDecimal.ZERO.setScale(2);
    }

    @PrePersist
    void beforeCreate() {
        if (loanRepaymentId == null) loanRepaymentId = UUID.randomUUID().toString();
        if (status == null) status = LoanRepaymentStatus.PENDING;
        if (principalApplied == null) principalApplied = BigDecimal.ZERO.setScale(2);
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public void complete(String transactionId, String transactionReference, BigDecimal principalApplied) {
        this.transactionId = transactionId;
        this.transactionReference = transactionReference;
        this.principalApplied = principalApplied;
        status = LoanRepaymentStatus.SUCCESS;
        failureReason = null;
        completedAt = Instant.now();
    }
    public void fail(String reason) {
        status = LoanRepaymentStatus.FAILED;
        failureReason = trim(reason);
    }
    public void cancel(String reason) {
        status = LoanRepaymentStatus.CANCELLED;
        failureReason = trim(reason);
    }
    public void reverse(String reason) {
        status = LoanRepaymentStatus.REVERSED;
        failureReason = trim(reason);
        reversedAt = Instant.now();
    }
    private String trim(String value) { return value == null ? null : value.substring(0, Math.min(500, value.length())); }

    public String getLoanRepaymentId() { return loanRepaymentId; }
    public Loan getLoan() { return loan; }
    public String getCustomerUserId() { return customerUserId; }
    public String getSourceAccountId() { return sourceAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getWorkflowReference() { return workflowReference; }
    public String getTransactionId() { return transactionId; }
    public String getTransactionReference() { return transactionReference; }
    public LoanRepaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public BigDecimal getPrincipalApplied() { return principalApplied; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getReversedAt() { return reversedAt; }
}
