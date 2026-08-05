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
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Check(constraints = "REQUESTED_AMOUNT > 0 AND MONTHLY_INCOME >= 0 AND REQUESTED_TENURE_MONTHS > 0 AND (APPROVED_AMOUNT IS NULL OR APPROVED_AMOUNT > 0) AND (APPROVED_TENURE_MONTHS IS NULL OR APPROVED_TENURE_MONTHS > 0) AND (APPROVED_ANNUAL_INTEREST_RATE IS NULL OR APPROVED_ANNUAL_INTEREST_RATE >= 0)")
@Table(
        name = "LOAN_APPLICATIONS",
        indexes = {
                @Index(name = "IX_LOAN_APP_CUSTOMER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"),
                @Index(name = "IX_LOAN_APP_ACCOUNT_STATUS", columnList = "LINKED_ACCOUNT_ID, STATUS"),
                @Index(name = "IX_LOAN_APP_CREATED", columnList = "CREATED_AT")
        })
public class LoanApplication {
    @Id
    @Column(name = "APPLICATION_ID", length = 36)
    private String applicationId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "LINKED_ACCOUNT_ID", nullable = false, length = 36)
    private String linkedAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "LOAN_TYPE", nullable = false, length = 30)
    private LoanType loanType;

    @Column(name = "REQUESTED_AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "REQUESTED_TENURE_MONTHS", nullable = false)
    private int requestedTenureMonths;

    @Column(name = "MONTHLY_INCOME", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(name = "PURPOSE", nullable = false, length = 500)
    private String purpose;

    @Column(name = "APPROVED_AMOUNT", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "APPROVED_ANNUAL_INTEREST_RATE", precision = 7, scale = 4)
    private BigDecimal approvedAnnualInterestRate;

    @Column(name = "APPROVED_TENURE_MONTHS")
    private Integer approvedTenureMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanApplicationStatus status;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    @Column(name = "DECISION_NOTES", length = 500)
    private String decisionNotes;

    @Column(name = "ISSUED_LOAN_ID", length = 36)
    private String issuedLoanId;

    @Column(name = "DECIDED_BY_USER_ID", length = 36)
    private String decidedByUserId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "DECIDED_AT")
    private Instant decidedAt;

    @PrePersist
    void beforeCreate() {
        if (applicationId == null) applicationId = UUID.randomUUID().toString();
        if (status == null) status = LoanApplicationStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void approve(String adminUserId, String loanId, BigDecimal amount, BigDecimal rate, int tenure, String notes) {
        status = LoanApplicationStatus.APPROVED;
        issuedLoanId = loanId;
        approvedAmount = amount;
        approvedAnnualInterestRate = rate;
        approvedTenureMonths = tenure;
        decisionNotes = notes;
        decidedByUserId = adminUserId;
        decidedAt = Instant.now();
        rejectionReason = null;
    }

    public void reject(String adminUserId, String reason) {
        status = LoanApplicationStatus.REJECTED;
        rejectionReason = reason;
        decidedByUserId = adminUserId;
        decidedAt = Instant.now();
    }

    public String getApplicationId() { return applicationId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getLinkedAccountId() { return linkedAccountId; }
    public void setLinkedAccountId(String linkedAccountId) { this.linkedAccountId = linkedAccountId; }
    public LoanType getLoanType() { return loanType; }
    public void setLoanType(LoanType loanType) { this.loanType = loanType; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public int getRequestedTenureMonths() { return requestedTenureMonths; }
    public void setRequestedTenureMonths(int requestedTenureMonths) { this.requestedTenureMonths = requestedTenureMonths; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(BigDecimal monthlyIncome) { this.monthlyIncome = monthlyIncome; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public BigDecimal getApprovedAnnualInterestRate() { return approvedAnnualInterestRate; }
    public Integer getApprovedTenureMonths() { return approvedTenureMonths; }
    public LoanApplicationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public String getDecisionNotes() { return decisionNotes; }
    public String getIssuedLoanId() { return issuedLoanId; }
    public String getDecidedByUserId() { return decidedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
