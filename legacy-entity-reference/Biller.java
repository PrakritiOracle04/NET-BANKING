package com.oracle.banking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "BILLER")
public class Biller {

    @Id
    @Column(name = "BILLER_ID")
    private String billerId;

    @Column(name = "BILLER_NAME", nullable = false, length = 100)
    private String billerName;

    @Column(name = "CATEGORY", length = 50)
    private String category;

    @Column(name = "STATUS", length = 20)
    private String status;

    public Biller() {
    }

    public Biller(String billerId, String billerName, String category, String status) {
        this.billerId = billerId;
        this.billerName = billerName;
        this.category = category;
        this.status = status;
    }

    public String getBillerId() {
        return billerId;
    }

    public void setBillerId(String billerId) {
        this.billerId = billerId;
    }

    public String getBillerName() {
        return billerName;
    }

    public void setBillerName(String billerName) {
        this.billerName = billerName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Biller{" +
                "billerId='" + billerId + '\'' +
                ", billerName='" + billerName + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}