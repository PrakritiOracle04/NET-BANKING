package com.oracle.banking.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "CUSTOMER_KYC")
public class CustomerKyc {
    @Id
    @Column(name = "KYC_ID", length = 36)
    private String kycId;

    @Column(name = "USER_ID", nullable = false, unique = true, length = 36)
    private String userId;

    @Column(name = "AADHAAR_ENCRYPTED", nullable = false, length = 500)
    private String aadhaarEncrypted;

    @Column(name = "AADHAAR_LAST4", nullable = false, length = 4)
    private String aadhaarLast4;

    @Column(name = "AADHAAR_HASH", nullable = false, unique = true, length = 64)
    private String aadhaarHash;

    @Column(name = "PAN_ENCRYPTED", nullable = false, length = 500)
    private String panEncrypted;

    @Column(name = "PAN_LAST4", nullable = false, length = 4)
    private String panLast4;

    @Column(name = "PAN_HASH", nullable = false, unique = true, length = 64)
    private String panHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "KYC_STATUS", nullable = false, length = 20)
    private KycStatus status;

    @Column(name = "REJECTION_REASON", length = 240)
    private String rejectionReason;

    @Column(name = "VERIFIED_AT")
    private Instant verifiedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected CustomerKyc() {}

    public CustomerKyc(String userId) {
        kycId = UUID.randomUUID().toString();
        this.userId = userId;
        status = KycStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    public void submit(String aadhaarEncrypted, String aadhaarLast4, String aadhaarHash,
            String panEncrypted, String panLast4, String panHash) {
        this.aadhaarEncrypted = aadhaarEncrypted;
        this.aadhaarLast4 = aadhaarLast4;
        this.aadhaarHash = aadhaarHash;
        this.panEncrypted = panEncrypted;
        this.panLast4 = panLast4;
        this.panHash = panHash;
        status = KycStatus.PENDING;
        rejectionReason = null;
        verifiedAt = null;
    }

    public void verify() {
        status = KycStatus.VERIFIED;
        rejectionReason = null;
        verifiedAt = Instant.now();
    }

    public void reject(String reason) {
        status = KycStatus.REJECTED;
        rejectionReason = reason;
        verifiedAt = null;
    }

    public void markDocumentsResubmitted() {
        if (status == KycStatus.REJECTED) {
            status = KycStatus.PENDING;
            rejectionReason = null;
            verifiedAt = null;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getKycId() { return kycId; }
    public String getUserId() { return userId; }
    public String getAadhaarLast4() { return aadhaarLast4; }
    public String getPanLast4() { return panLast4; }
    public String getAadhaarHash() { return aadhaarHash; }
    public String getPanHash() { return panHash; }
    public KycStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
