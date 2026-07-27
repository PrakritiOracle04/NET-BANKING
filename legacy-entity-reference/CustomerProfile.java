package com.oracle.banking.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.sql.Date;

@Entity
@Table(name = "CUSTOMER_PROFILE")
public class CustomerProfile implements Serializable {

    @Id
    @Column(name = "CUSTOMER_ID")
    private String customerId;

    @OneToOne
    @JoinColumn(name = "USER_ID")
    private AppUser user;

    @Column(name = "FULL_NAME")
    private String fullName;

    @Column(name = "DOB")
    private Date dob;

    @Column(name = "KYC_STATUS")
    private String kycStatus;

    @Column(name = "PROFILE_STATUS")
    private String profileStatus;

    @Column(name = "ADDRESS_TYPE")
    private String addressType;

    @Column(name = "ADDRESS_LINE1")
    private String addressLine1;

    @Column(name = "ADDRESS_LINE2")
    private String addressLine2;

    @Column(name = "CITY")
    private String city;

    @Column(name = "STATE")
    private String state;

    @Column(name = "COUNTRY")
    private String country;

    @Column(name = "POSTAL_CODE")
    private String postalCode;

    @Column(name = "EMAIL_ENABLED")
    private Integer emailEnabled;

    @Column(name = "SMS_ENABLED")
    private Integer smsEnabled;

    @Column(name = "IN_APP_ENABLED")
    private Integer inAppEnabled;

    public CustomerProfile() {
    }

    public CustomerProfile(String customerId, AppUser user, String fullName) {
        this.customerId = customerId;
        this.user = user;
        this.fullName = fullName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Date getDob() {
        return dob;
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getProfileStatus() {
        return profileStatus;
    }

    public void setProfileStatus(String profileStatus) {
        this.profileStatus = profileStatus;
    }

    public String getAddressType() {
        return addressType;
    }

    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public Integer getEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(Integer emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public Integer getSmsEnabled() {
        return smsEnabled;
    }

    public void setSmsEnabled(Integer smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    public Integer getInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(Integer inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }
}