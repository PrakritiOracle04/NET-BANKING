package com.oracle.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Check(constraints = "PRINCIPAL_AMOUNT > 0 AND ANNUAL_INTEREST_RATE >= 0 AND TENURE_MONTHS > 0 AND EMI_AMOUNT >= 0 AND OUTSTANDING_BALANCE >= 0")
@Table(
        name = "LOANS",
        indexes = {
            @Index(name = "IX_LOAN_OWNER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"),
            @Index(name = "IX_LOAN_LINKED_ACCOUNT", columnList = "LINKED_ACCOUNT_ID")
        })
public class Loan {
    @Id
    @Column(name = "LOAN_ID", length = 36)
    private String loanId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "LINKED_ACCOUNT_ID", nullable = false, length = 36)
    private String linkedAccountId;

    @Column(name = "LOAN_NUMBER", nullable = false, unique = true, length = 30)
    private String loanNumber;

    @Column(name = "PRINCIPAL_AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "ANNUAL_INTEREST_RATE", nullable = false, precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "TENURE_MONTHS", nullable = false)
    private Integer tenureMonths;

    @Column(name = "EMI_AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "OUTSTANDING_BALANCE", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "MATURITY_DATE", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "CLOSED_AT")
    private Instant closedAt;

    protected Loan() {
    }

    public Loan(String customerUserId, String linkedAccountId, String loanNumber, BigDecimal principalAmount,
            BigDecimal annualInterestRate, int tenureMonths, BigDecimal emiAmount, LocalDate startDate) {
        this.loanId = UUID.randomUUID().toString();
        this.customerUserId = customerUserId;
        this.linkedAccountId = linkedAccountId;
        this.loanNumber = loanNumber;
        this.principalAmount = principalAmount;
        this.annualInterestRate = annualInterestRate;
        this.tenureMonths = tenureMonths;
        this.emiAmount = emiAmount;
        this.outstandingBalance = principalAmount;
        this.startDate = startDate;
        this.maturityDate = startDate.plusMonths(tenureMonths);
        this.status = LoanStatus.ACTIVE;
    }

    @PrePersist
    void beforeCreate() {
        if (loanId == null) loanId = UUID.randomUUID().toString();
        if (status == null) status = LoanStatus.ACTIVE;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public boolean ownedBy(String userId) { return customerUserId.equals(userId); }
    public boolean closed() { return status == LoanStatus.CLOSED; }
    public void reduceOutstanding(BigDecimal principalApplied) {
        outstandingBalance = outstandingBalance.subtract(principalApplied).max(BigDecimal.ZERO).setScale(2);
        if (outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
            status = LoanStatus.CLOSED;
            closedAt = Instant.now();
        }
    }
    public void increaseOutstanding(BigDecimal principalAmount) {
        outstandingBalance = outstandingBalance.add(principalAmount).setScale(2);
        if (status == LoanStatus.CLOSED) {
            status = LoanStatus.ACTIVE;
            closedAt = null;
        }
    }
    public void updateStatus(LoanStatus newStatus) { status = newStatus; if (newStatus != LoanStatus.CLOSED) closedAt = null; }
    public void recalculateStatus(boolean hasOverdue) {
        if (outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
            status = LoanStatus.CLOSED;
            if (closedAt == null) closedAt = Instant.now();
        } else if (status != LoanStatus.DEFAULTED) {
            status = hasOverdue ? LoanStatus.OVERDUE : LoanStatus.ACTIVE;
            closedAt = null;
        }
    }

    public String getLoanId() { return loanId; }
    public String getCustomerUserId() { return customerUserId; }
    public String getLinkedAccountId() { return linkedAccountId; }
    public String getLoanNumber() { return loanNumber; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public Integer getTenureMonths() { return tenureMonths; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public LoanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
}
