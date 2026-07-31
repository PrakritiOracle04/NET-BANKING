package com.oracle.banking.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ACCOUNT_MOVEMENTS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_MOVEMENT_ACCOUNT_REFERENCE",
                columnNames = {"ACCOUNT_ID", "REFERENCE_NUMBER"}))
public class AccountMovement {
    @Id
    @Column(name = "MOVEMENT_ID", length = 36)
    private String movementId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 36)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ACCOUNT_ID",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_MOVEMENT_ACCOUNT"))
    private Account account;

    @Column(name = "REFERENCE_NUMBER", nullable = false, length = 80)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION", nullable = false, length = 10)
    private BalanceOperation operation;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "DESCRIPTION", length = 160)
    private String description;

    @Column(name = "REVERSED", nullable = false)
    private boolean reversed;

    @Column(name = "REVERSAL_REFERENCE", length = 80)
    private String reversalReference;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected AccountMovement() {
    }

    public AccountMovement(String accountId, String referenceNumber, BalanceOperation operation, BigDecimal amount, String description) {
        this.movementId = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.referenceNumber = referenceNumber;
        this.operation = operation;
        this.amount = amount;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public String getReferenceNumber() { return referenceNumber; }
    public BalanceOperation getOperation() { return operation; }
    public BigDecimal getAmount() { return amount; }
    public boolean isReversed() { return reversed; }
    public void markReversed(String reference) { this.reversed = true; this.reversalReference = reference; }
}
