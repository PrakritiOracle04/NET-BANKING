package com.oracle.banking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "BANK_TRANSACTION")
public class BankTransaction {

    @Id
    @Column(name = "TRANSACTION_ID")
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    @Column(name = "TRANSACTION_TYPE", length = 30)
    private String transactionType;

    @Column(name = "REFERENCE_ID", length = 36)
    private String referenceId;

    @Column(name = "REFERENCE_TYPE", length = 30)
    private String referenceType;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "DEBIT_CREDIT", length = 1)
    private String debitCredit;

    @Column(name = "TRANSACTION_DATE")
    private LocalDateTime transactionDate;

    public BankTransaction() {
    }

    public BankTransaction(String transactionId, Account account,
                           String transactionType, String referenceId,
                           String referenceType, BigDecimal amount,
                           String status, String debitCredit,
                           LocalDateTime transactionDate) {
        this.transactionId = transactionId;
        this.account = account;
        this.transactionType = transactionType;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.amount = amount;
        this.status = status;
        this.debitCredit = debitCredit;
        this.transactionDate = transactionDate;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDebitCredit() {
        return debitCredit;
    }

    public void setDebitCredit(String debitCredit) {
        this.debitCredit = debitCredit;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    @Override
    public String toString() {
        return "BankTransaction{" +
                "transactionId='" + transactionId + '\'' +
                ", transactionType='" + transactionType + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", debitCredit='" + debitCredit + '\'' +
                ", transactionDate=" + transactionDate +
                '}';
    }
}