package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "AUTH_FACTOR")
public class AuthFactor {

    @Id
    @Column(name = "AUTH_FACTOR_ID")
    private String authFactorId;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Column(name = "FACTOR_TYPE", nullable = false, length = 30)
    private String factorType;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public AuthFactor() {
    }

    public AuthFactor(String authFactorId, AppUser user,
                      String factorType, LocalDateTime createdAt) {
        this.authFactorId = authFactorId;
        this.user = user;
        this.factorType = factorType;
        this.createdAt = createdAt;
    }

    public String getAuthFactorId() {
        return authFactorId;
    }

    public void setAuthFactorId(String authFactorId) {
        this.authFactorId = authFactorId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getFactorType() {
        return factorType;
    }

    public void setFactorType(String factorType) {
        this.factorType = factorType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AuthFactor{" +
                "authFactorId='" + authFactorId + '\'' +
                ", factorType='" + factorType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}