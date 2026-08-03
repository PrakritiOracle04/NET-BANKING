package com.oracle.banking.billpayment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "CUSTOMER_BILLERS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CUSTOMER_BILLER_REFERENCE",
                columnNames = {"CUSTOMER_USER_ID", "BILLER_ID", "CONSUMER_REFERENCE"}),
        indexes = @Index(name = "IX_CUSTOMER_BILLER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"))
public class CustomerBiller {
    @Id
    @Column(name = "CUSTOMER_BILLER_ID", length = 36)
    private String customerBillerId;

    @Column(name = "CUSTOMER_USER_ID", nullable = false, length = 36)
    private String customerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BILLER_ID", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_CUSTOMER_BILLER_CATALOG"))
    private BillerCatalog biller;

    @Column(name = "CONSUMER_REFERENCE", nullable = false, length = 80)
    private String consumerReference;

    @Column(name = "NICKNAME", nullable = false, length = 80)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CustomerBillerStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void beforeCreate() {
        if (customerBillerId == null) customerBillerId = UUID.randomUUID().toString();
        if (status == null) status = CustomerBillerStatus.ACTIVE;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getCustomerBillerId() { return customerBillerId; }
    public String getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(String customerUserId) { this.customerUserId = customerUserId; }
    public BillerCatalog getBiller() { return biller; }
    public void setBiller(BillerCatalog biller) { this.biller = biller; }
    public String getConsumerReference() { return consumerReference; }
    public void setConsumerReference(String consumerReference) { this.consumerReference = consumerReference; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public CustomerBillerStatus getStatus() { return status; }
    public void setStatus(CustomerBillerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
