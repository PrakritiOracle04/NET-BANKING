package com.oracle.banking.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "LOAN")
public class Loan {

    @Id
    @Column(name = "LOAN_ID")
    private String loanId;

    @ManyToOne
    @JoinColumn(name = "CUSTOMER_ID", nullable = false)
    private CustomerProfile customer;

    @Column(name = "LOAN_TYPE", length = 30)
    private String loanType;

    @Column(name = "PRINCIPAL_AMOUNT", nullable = false)
    private BigDecimal principalAmount;

    @Column(name = "OUTSTANDING_AMOUNT")
    private BigDecimal outstandingAmount;

    @Column(name = "STATUS", length = 20)
    private String status;

    public Loan() {
    }

    public Loan(String loanId, CustomerProfile customer, String loanType,
                BigDecimal principalAmount, BigDecimal outstandingAmount,
                String status) {
        this.loanId = loanId;
        this.customer = customer;
        this.loanType = loanType;
        this.principalAmount = principalAmount;
        this.outstandingAmount = outstandingAmount;
        this.status = status;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerProfile customer) {
        this.customer = customer;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Loan{" +
                "loanId='" + loanId + '\'' +
                ", loanType='" + loanType + '\'' +
                ", principalAmount=" + principalAmount +
                ", outstandingAmount=" + outstandingAmount +
                ", status='" + status + '\'' +
                '}';
    }
}