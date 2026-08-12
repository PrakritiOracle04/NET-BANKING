package com.oracle.banking.account.entity;

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

@Entity
@Table(
        name = "ACCOUNTS",
        indexes = @Index(
                name = "IX_ACCOUNT_OWNER_PRIMARY",
                columnList = "CUSTOMER_USER_ID, IS_PRIMARY"))
public class Account {
    @Id
    @Column(name = "ACCOUNT_ID", length = 36)
    private String accountId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "BRANCH_IFSC", nullable = false, length = 11)
    private String branchIfsc;

    @Column(name = "OPENING_REFERENCE", nullable = false, unique = true, length = 80)
    private String openingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "AVAILABLE_BALANCE", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "LEDGER_BALANCE", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalance;

    @Column(name = "IS_PRIMARY", nullable = false)
    private boolean primaryAccount;

    @Column(name = "CREATED_VIA", length = 40)
    private String createdVia;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void beforeCreate() {
        if (accountId == null) accountId = UUID.randomUUID().toString();
        if (status == null) status = AccountStatus.ACTIVE;
        if (availableBalance == null) availableBalance = BigDecimal.ZERO;
        if (ledgerBalance == null) ledgerBalance = availableBalance;
        if (createdVia == null) createdVia = "ADMIN";
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public String getBranchIfsc() { return branchIfsc; }
    public void setBranchIfsc(String branchIfsc) { this.branchIfsc = branchIfsc; }
    public String getOpeningReference() { return openingReference; }
    public void setOpeningReference(String openingReference) { this.openingReference = openingReference; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public BigDecimal getLedgerBalance() { return ledgerBalance; }
    public void setLedgerBalance(BigDecimal ledgerBalance) { this.ledgerBalance = ledgerBalance; }
    public boolean isPrimaryAccount() { return primaryAccount; }
    public void setPrimaryAccount(boolean primaryAccount) { this.primaryAccount = primaryAccount; }
    public String getCreatedVia() { return createdVia; }
    public void setCreatedVia(String createdVia) { this.createdVia = createdVia; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
