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
@Check(constraints = "DAILY_TRANSACTION_LIMIT > 0 AND EXPIRY_MONTH BETWEEN 1 AND 12")
@Table(
        name = "CARDS",
        indexes = {
            @Index(name = "IX_CARD_OWNER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"),
            @Index(name = "IX_CARD_ACCOUNT_STATUS", columnList = "ACCOUNT_ID, STATUS")
        })
public class BankCard {
    @Id
    @Column(name = "CARD_ID", length = 36)
    private String cardId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 36)
    private String accountId;

    @Column(name = "CARD_NUMBER_ENCRYPTED", nullable = false, length = 500)
    private String cardNumberEncrypted;

    @Column(name = "CARD_NUMBER_HASH", nullable = false, unique = true, length = 64)
    private String cardNumberHash;

    @Column(name = "LAST_FOUR_DIGITS", nullable = false, length = 4)
    private String lastFourDigits;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_TYPE", nullable = false, length = 20)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_PRODUCT", length = 20)
    private CardProduct cardProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CardStatus status;

    @Column(name = "DAILY_TRANSACTION_LIMIT", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "EXPIRY_MONTH", nullable = false)
    private int expiryMonth;

    @Column(name = "EXPIRY_YEAR", nullable = false)
    private int expiryYear;

    @Column(name = "BLOCKED_REASON", length = 240)
    private String blockedReason;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Column(name = "ACTIVATED_AT")
    private Instant activatedAt;

    @Column(name = "BLOCKED_AT")
    private Instant blockedAt;

    @PrePersist
    void beforeCreate() {
        if (cardId == null) cardId = UUID.randomUUID().toString();
        if (status == null) status = CardStatus.INACTIVE;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public void activate() {
        status = CardStatus.ACTIVE;
        activatedAt = Instant.now();
        blockedAt = null;
        blockedReason = null;
    }

    public void block(String reason) {
        status = CardStatus.BLOCKED;
        blockedAt = Instant.now();
        blockedReason = reason;
    }

    public void expire() {
        status = CardStatus.EXPIRED;
        blockedReason = null;
    }

    public String getCardId() { return cardId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setCardNumberEncrypted(String cardNumberEncrypted) { this.cardNumberEncrypted = cardNumberEncrypted; }
    public String getCardNumberHash() { return cardNumberHash; }
    public void setCardNumberHash(String cardNumberHash) { this.cardNumberHash = cardNumberHash; }
    public String getLastFourDigits() { return lastFourDigits; }
    public void setLastFourDigits(String lastFourDigits) { this.lastFourDigits = lastFourDigits; }
    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }
    public CardProduct getCardProduct() { return cardProduct; }
    public void setCardProduct(CardProduct cardProduct) { this.cardProduct = cardProduct; }
    public CardStatus getStatus() { return status; }
    public BigDecimal getDailyTransactionLimit() { return dailyTransactionLimit; }
    public void setDailyTransactionLimit(BigDecimal dailyTransactionLimit) { this.dailyTransactionLimit = dailyTransactionLimit; }
    public int getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(int expiryMonth) { this.expiryMonth = expiryMonth; }
    public int getExpiryYear() { return expiryYear; }
    public void setExpiryYear(int expiryYear) { this.expiryYear = expiryYear; }
    public String getBlockedReason() { return blockedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getBlockedAt() { return blockedAt; }
}
