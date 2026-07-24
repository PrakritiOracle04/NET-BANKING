package com.oracle.banking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BENEFICIARY")
public class Beneficiary {

    @Id
    @Column(name = "BENEFICIARY_ID")
    private String beneficiaryId;

    @ManyToOne
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    private CustomerProfile customer;

    @Column(name = "BENEFICIARY_NAME", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "BANK_NAME", length = 100)
    private String bankName;

    @Column(name = "STATUS", length = 20)
    private String status;

    public Beneficiary() {
    }

    public Beneficiary(String beneficiaryId, CustomerProfile customer,
                       String beneficiaryName, String accountNumber,
                       String bankName, String status) {
        this.beneficiaryId = beneficiaryId;
        this.customer = customer;
        this.beneficiaryName = beneficiaryName;
        this.accountNumber = accountNumber;
        this.bankName = bankName;
        this.status = status;
    }

    public String getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(String beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerProfile customer) {
        this.customer = customer;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Beneficiary{" +
                "beneficiaryId='" + beneficiaryId + '\'' +
                ", beneficiaryName='" + beneficiaryName + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", bankName='" + bankName + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}