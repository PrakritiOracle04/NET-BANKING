package com.oracle.banking.card.entity;

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
@Check(constraints = "CREDIT_LIMIT > 0 AND AVAILABLE_CREDIT >= 0 AND OUTSTANDING_BALANCE >= 0 AND BILLING_CYCLE_DAY BETWEEN 1 AND 28")
@Table(
        name = "CREDIT_CARD_ACCOUNTS",
        indexes = {
                @Index(name = "IX_CREDIT_CARD_ACCOUNT_OWNER", columnList = "CUSTOMER_USER_ID, STATUS"),
                @Index(name = "IX_CREDIT_CARD_ACCOUNT_CARD", columnList = "CARD_ID")
        })
public class CreditCardAccount {
    @Id
    @Column(name = "CREDIT_ACCOUNT_ID", length = 36)
    private String creditAccountId;

    @Column(name = "CARD_ID", nullable = false, unique = true, length = 36)
    private String cardId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "LINKED_ACCOUNT_ID", nullable = false, length = 36)
    private String linkedAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_PRODUCT", nullable = false, length = 20)
    private CardProduct cardProduct;

    @Column(name = "CREDIT_LIMIT", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "AVAILABLE_CREDIT", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableCredit;

    @Column(name = "OUTSTANDING_BALANCE", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(name = "BILLING_CYCLE_DAY", nullable = false)
    private int billingCycleDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CreditCardAccountStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void beforeCreate() {
        if (creditAccountId == null) creditAccountId = UUID.randomUUID().toString();
        if (availableCredit == null) availableCredit = creditLimit;
        if (outstandingBalance == null) outstandingBalance = BigDecimal.ZERO;
        if (status == null) status = CreditCardAccountStatus.ACTIVE;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getCreditAccountId() { return creditAccountId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getLinkedAccountId() { return linkedAccountId; }
    public void setLinkedAccountId(String linkedAccountId) { this.linkedAccountId = linkedAccountId; }
    public CardProduct getCardProduct() { return cardProduct; }
    public void setCardProduct(CardProduct cardProduct) { this.cardProduct = cardProduct; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getAvailableCredit() { return availableCredit; }
    public void setAvailableCredit(BigDecimal availableCredit) { this.availableCredit = availableCredit; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }
    public int getBillingCycleDay() { return billingCycleDay; }
    public void setBillingCycleDay(int billingCycleDay) { this.billingCycleDay = billingCycleDay; }
    public CreditCardAccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
