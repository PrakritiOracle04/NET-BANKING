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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Check(constraints = "INSTALLMENT_NUMBER > 0 AND OPENING_BALANCE >= 0 AND PRINCIPAL_DUE >= 0 AND INTEREST_DUE >= 0 AND TOTAL_DUE >= 0 AND AMOUNT_PAID >= 0")
@Table(
        name = "EMI_SCHEDULES",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_EMI_LOAN_INSTALLMENT",
                columnNames = {"LOAN_ID", "INSTALLMENT_NUMBER"}),
        indexes = {
            @Index(name = "IX_EMI_LOAN_STATUS_DUE", columnList = "LOAN_ID, STATUS, DUE_DATE"),
            @Index(name = "IX_EMI_STATUS_DUE", columnList = "STATUS, DUE_DATE")
        })
public class EmiSchedule {
    @Id
    @Column(name = "EMI_SCHEDULE_ID", length = 36)
    private String emiScheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LOAN_ID", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_EMI_LOAN"))
    private Loan loan;

    @Column(name = "INSTALLMENT_NUMBER", nullable = false)
    private Integer installmentNumber;

    @Column(name = "DUE_DATE", nullable = false)
    private LocalDate dueDate;

    @Column(name = "OPENING_BALANCE", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "PRINCIPAL_DUE", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "INTEREST_DUE", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "TOTAL_DUE", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDue;

    @Column(name = "AMOUNT_PAID", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private EmiStatus status = EmiStatus.PENDING;

    @Column(name = "PAID_AT")
    private Instant paidAt;

    @Column(name = "REMINDER_SENT_AT")
    private Instant reminderSentAt;

    @Column(name = "OVERDUE_NOTIFIED_AT")
    private Instant overdueNotifiedAt;

    protected EmiSchedule() {
    }

    public EmiSchedule(Loan loan, int installmentNumber, LocalDate dueDate, BigDecimal openingBalance,
            BigDecimal principalDue, BigDecimal interestDue) {
        this.emiScheduleId = UUID.randomUUID().toString();
        this.loan = loan;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.openingBalance = openingBalance;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.totalDue = principalDue.add(interestDue).setScale(2);
        this.amountPaid = BigDecimal.ZERO.setScale(2);
        this.status = EmiStatus.PENDING;
    }

    @PrePersist
    void beforeCreate() {
        if (emiScheduleId == null) emiScheduleId = UUID.randomUUID().toString();
        if (amountPaid == null) amountPaid = BigDecimal.ZERO.setScale(2);
        if (status == null) status = EmiStatus.PENDING;
    }

    public BigDecimal remainingDue() { return totalDue.subtract(amountPaid).max(BigDecimal.ZERO).setScale(2); }
    public BigDecimal remainingInterest() {
        BigDecimal interestPaid = amountPaid.min(interestDue);
        return interestDue.subtract(interestPaid).max(BigDecimal.ZERO).setScale(2);
    }
    public BigDecimal remainingPrincipal() {
        BigDecimal principalPaid = amountPaid.subtract(interestDue).max(BigDecimal.ZERO);
        return principalDue.subtract(principalPaid).max(BigDecimal.ZERO).setScale(2);
    }
    public BigDecimal apply(BigDecimal amount) {
        BigDecimal applied = amount.min(remainingDue()).setScale(2);
        BigDecimal principalBefore = remainingPrincipal();
        amountPaid = amountPaid.add(applied).setScale(2);
        status = amountPaid.compareTo(totalDue) >= 0 ? EmiStatus.PAID : EmiStatus.PARTIALLY_PAID;
        if (status == EmiStatus.PAID) paidAt = Instant.now();
        return principalBefore.subtract(remainingPrincipal()).max(BigDecimal.ZERO).setScale(2);
    }
    public BigDecimal reverse(BigDecimal amount) {
        BigDecimal reversed = amount.min(amountPaid).setScale(2);
        BigDecimal principalBefore = remainingPrincipal();
        amountPaid = amountPaid.subtract(reversed).max(BigDecimal.ZERO).setScale(2);
        paidAt = null;
        status = amountPaid.compareTo(BigDecimal.ZERO) == 0 ? EmiStatus.PENDING : EmiStatus.PARTIALLY_PAID;
        return remainingPrincipal().subtract(principalBefore).max(BigDecimal.ZERO).setScale(2);
    }
    public void markOverdue() { if (status == EmiStatus.PENDING || status == EmiStatus.PARTIALLY_PAID) status = EmiStatus.OVERDUE; }
    public void markReminderSent() { reminderSentAt = Instant.now(); }
    public void markOverdueNotified() { overdueNotifiedAt = Instant.now(); }

    public String getEmiScheduleId() { return emiScheduleId; }
    public Loan getLoan() { return loan; }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getPrincipalDue() { return principalDue; }
    public BigDecimal getInterestDue() { return interestDue; }
    public BigDecimal getTotalDue() { return totalDue; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public EmiStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getOverdueNotifiedAt() { return overdueNotifiedAt; }
}
