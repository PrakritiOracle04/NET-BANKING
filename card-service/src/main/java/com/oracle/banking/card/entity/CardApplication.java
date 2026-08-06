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
@Check(constraints = "ANNUAL_INCOME >= 0 AND (REQUESTED_DAILY_LIMIT IS NULL OR REQUESTED_DAILY_LIMIT > 0) AND (APPROVED_DAILY_LIMIT IS NULL OR APPROVED_DAILY_LIMIT > 0) AND (APPROVED_CREDIT_LIMIT IS NULL OR APPROVED_CREDIT_LIMIT > 0)")
@Table(
        name = "CARD_APPLICATIONS",
        indexes = {
                @Index(name = "IX_CARD_APP_CUSTOMER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"),
                @Index(name = "IX_CARD_APP_ACCOUNT_STATUS", columnList = "ACCOUNT_ID, STATUS"),
                @Index(name = "IX_CARD_APP_CREATED", columnList = "CREATED_AT")
        })
public class CardApplication {
    @Id
    @Column(name = "APPLICATION_ID", length = 36)
    private String applicationId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_TYPE", nullable = false, length = 20)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_PRODUCT", nullable = false, length = 20)
    private CardProduct cardProduct;

    @Column(name = "REQUESTED_DAILY_LIMIT", precision = 19, scale = 2)
    private BigDecimal requestedDailyLimit;

    @Column(name = "APPROVED_DAILY_LIMIT", precision = 19, scale = 2)
    private BigDecimal approvedDailyLimit;

    @Column(name = "APPROVED_CREDIT_LIMIT", precision = 19, scale = 2)
    private BigDecimal approvedCreditLimit;

    @Column(name = "ANNUAL_INCOME", nullable = false, precision = 19, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "OCCUPATION", length = 120)
    private String occupation;

    @Column(name = "DELIVERY_ADDRESS", nullable = false, length = 500)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CardApplicationStatus status;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    @Column(name = "DECISION_NOTES", length = 500)
    private String decisionNotes;

    @Column(name = "ISSUED_CARD_ID", length = 36)
    private String issuedCardId;

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
        if (cardType == null) cardType = CardType.DEBIT;
        if (status == null) status = CardApplicationStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void approve(String adminUserId, String cardId, BigDecimal dailyLimit, BigDecimal creditLimit, String notes) {
        status = CardApplicationStatus.APPROVED;
        issuedCardId = cardId;
        approvedDailyLimit = dailyLimit;
        approvedCreditLimit = creditLimit;
        decidedByUserId = adminUserId;
        decisionNotes = notes;
        decidedAt = Instant.now();
        rejectionReason = null;
    }

    public void reject(String adminUserId, String reason) {
        status = CardApplicationStatus.REJECTED;
        rejectionReason = reason;
        decidedByUserId = adminUserId;
        decidedAt = Instant.now();
    }

    public String getApplicationId() { return applicationId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }
    public CardProduct getCardProduct() { return cardProduct; }
    public void setCardProduct(CardProduct cardProduct) { this.cardProduct = cardProduct; }
    public BigDecimal getRequestedDailyLimit() { return requestedDailyLimit; }
    public void setRequestedDailyLimit(BigDecimal requestedDailyLimit) { this.requestedDailyLimit = requestedDailyLimit; }
    public BigDecimal getApprovedDailyLimit() { return approvedDailyLimit; }
    public BigDecimal getApprovedCreditLimit() { return approvedCreditLimit; }
    public BigDecimal getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(BigDecimal annualIncome) { this.annualIncome = annualIncome; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public CardApplicationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public String getDecisionNotes() { return decisionNotes; }
    public String getIssuedCardId() { return issuedCardId; }
    public String getDecidedByUserId() { return decidedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
