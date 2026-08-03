package com.oracle.banking.beneficiary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "BENEFICIARIES",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_BENEFICIARY_OWNER_ACCOUNT",
                columnNames = {"CUSTOMER_USER_ID", "ACCOUNT_NUMBER"}),
        indexes = @Index(
                name = "IX_BENEFICIARY_OWNER_FAV",
                columnList = "CUSTOMER_USER_ID, FAVOURITE"))
public class Beneficiary {
    @Id
    @Column(name = "BENEFICIARY_ID", length = 36)
    private String beneficiaryId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @Column(name = "NICKNAME", nullable = false, length = 80)
    private String nickname;

    @Column(name = "BENEFICIARY_NAME", nullable = false, length = 120)
    private String beneficiaryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "RELATIONSHIP", nullable = false, length = 20)
    private BeneficiaryRelationship relationship;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "IFSC_CODE", nullable = false, length = 11)
    private String ifscCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private BeneficiaryStatus status;

    @Column(name = "FAVOURITE", nullable = false)
    private boolean favourite;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void beforeCreate() {
        if (beneficiaryId == null) beneficiaryId = UUID.randomUUID().toString();
        if (status == null) status = BeneficiaryStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(String beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public BeneficiaryRelationship getRelationship() { return relationship; }
    public void setRelationship(BeneficiaryRelationship relationship) { this.relationship = relationship; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }
    public BeneficiaryStatus getStatus() { return status; }
    public void setStatus(BeneficiaryStatus status) { this.status = status; }
    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
