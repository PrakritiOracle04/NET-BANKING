package com.oracle.banking.customer.entity;

import com.oracle.banking.customer.dto.CustomerDtos.Update;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "FATHER_OR_SPOUSE_NAME", length = 120)
    private String fatherOrSpouseName;

    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;

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

    @Column(name = "PROFILE_STATUS", nullable = false, length = 20)
    private String profileStatus;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected CustomerProfile() {}

    public CustomerProfile(String userId, String fullName) {
        this.customerId = UUID.randomUUID().toString();
        this.userId = userId;
        this.fullName = fullName;
        this.profileStatus = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(Update request) {
        fullName = request.fullName();
        fatherOrSpouseName = request.fatherOrSpouseName();
        dateOfBirth = request.dateOfBirth();
        addressLine1 = request.addressLine1();
        addressLine2 = request.addressLine2();
        city = request.city();
        state = request.state();
        country = request.country();
        postalCode = request.postalCode();
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isComplete() {
        return notBlank(fullName)
                && notBlank(fatherOrSpouseName)
                && dateOfBirth != null
                && notBlank(addressLine1)
                && notBlank(city)
                && notBlank(state)
                && notBlank(country)
                && notBlank(postalCode);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getCustomerId() { return customerId; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getFatherOrSpouseName() { return fatherOrSpouseName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getPostalCode() { return postalCode; }
    public String getProfileStatus() { return profileStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
