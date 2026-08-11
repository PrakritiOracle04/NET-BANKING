package com.oracle.banking.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "BILLER_CATALOG",
        indexes = @Index(name = "IX_BILLER_STATUS_CATEGORY", columnList = "STATUS, CATEGORY"))
public class BillerCatalog {
    @Id
    @Column(name = "BILLER_ID", length = 36)
    private String billerId;

    @Column(name = "BILLER_CODE", nullable = false, unique = true, length = 40)
    private String billerCode;

    @Column(name = "BILLER_NAME", nullable = false, length = 120)
    private String billerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private BillerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private BillerStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    public BillerCatalog() {}

    public BillerCatalog(String billerCode, String billerName, BillerCategory category) {
        updateCatalogDetails(billerName, category, BillerStatus.ACTIVE);
        this.billerCode = billerCode;
    }

    public void updateCatalogDetails(
            String billerName,
            BillerCategory category,
            BillerStatus status) {
        this.billerName = billerName;
        this.category = category;
        this.status = status;
    }

    @PrePersist
    void beforeCreate() {
        if (billerId == null) billerId = UUID.randomUUID().toString();
        if (status == null) status = BillerStatus.ACTIVE;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getBillerId() { return billerId; }
    public String getBillerCode() { return billerCode; }
    public void setBillerCode(String billerCode) { this.billerCode = billerCode; }
    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }
    public BillerCategory getCategory() { return category; }
    public void setCategory(BillerCategory category) { this.category = category; }
    public BillerStatus getStatus() { return status; }
    public void setStatus(BillerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
