package com.oracle.banking.customer.entity;

import com.oracle.banking.customer.dto.CustomerDtos.Update;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "CUSTOMER_PROFILE")
public class CustomerProfile {
    @Id
    @Column(name = "CUSTOMER_ID", length = 36)
    private String customerId;
    @Column(name = "USER_ID", nullable = false, unique = true, length = 36)
    private String userId;
    @Column(name = "FULL_NAME", nullable = false, length = 120)
    private String fullName;
    @Column(name = "EMAIL", nullable = false, length = 120)
    private String email;
    @Column(name = "PHONE", nullable = false, length = 20)
    private String phone;
    @Column(name = "ADDRESS_LINE1", length = 160)
    private String addressLine1;
    @Column(name = "ADDRESS_LINE2", length = 160)
    private String addressLine2;
    @Column(name = "CITY", length = 80)
    private String city;
    @Column(name = "STATE", length = 80)
    private String state;
    @Column(name = "COUNTRY", length = 80)
    private String country;
    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;
    @Column(name = "KYC_STATUS", nullable = false, length = 20)
    private String kycStatus;
    @Column(name = "PROFILE_STATUS", nullable = false, length = 20)
    private String profileStatus;
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected CustomerProfile() {
    }

    public CustomerProfile(String userId, String fullName, String email, String phone) {
        this.customerId = UUID.randomUUID().toString();
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.kycStatus = "PENDING";
        this.profileStatus = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(Update request) {
        this.fullName = request.fullName();
        this.phone = request.phone();
        this.addressLine1 = request.addressLine1();
        this.addressLine2 = request.addressLine2();
        this.city = request.city();
        this.state = request.state();
        this.country = request.country();
        this.postalCode = request.postalCode();
        this.updatedAt = Instant.now();
    }

    public String getCustomerId() { return customerId; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getPostalCode() { return postalCode; }
    public String getKycStatus() { return kycStatus; }
    public String getProfileStatus() { return profileStatus; }
}
