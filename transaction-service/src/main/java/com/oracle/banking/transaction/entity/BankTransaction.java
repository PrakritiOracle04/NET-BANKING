package com.oracle.banking.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "BANK_TRANSACTIONS")
public class BankTransaction {
    @Id
    @Column(name = "TRANSACTION_ID", length = 36)
    private String transactionId;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 36)
    private String accountId;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "CUSTOMER_USERNAME", nullable = false, length = 120)
    private String customerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "REFERENCE_NUMBER", nullable = false, unique = true, length = 80)
    private String referenceNumber;

    @Column(name = "REFERENCE_TYPE", length = 40)
    private String referenceType;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "DEBIT_CREDIT", nullable = false, length = 10)
    private DebitCredit debitCredit;

    @Column(name = "DESCRIPTION", length = 240)
    private String description;

    @Column(name = "TRANSACTION_DATE", nullable = false)
    private Instant transactionDate;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @PrePersist
    void beforeCreate() {
        if (transactionId == null) transactionId = UUID.randomUUID().toString();
        if (referenceNumber == null) referenceNumber = "TXN-" + UUID.randomUUID();
        if (status == null) status = TransactionStatus.SUCCESS;
        if (transactionDate == null) transactionDate = Instant.now();
        createdAt = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCustomerUsername() { return customerUsername; }
    public void setCustomerUsername(String customerUsername) { this.customerUsername = customerUsername; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public DebitCredit getDebitCredit() { return debitCredit; }
    public void setDebitCredit(DebitCredit debitCredit) { this.debitCredit = debitCredit; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getTransactionDate() { return transactionDate; }
    public void setTransactionDate(Instant transactionDate) { this.transactionDate = transactionDate; }
    public Instant getCreatedAt() { return createdAt; }
}
