package com.oracle.banking.twofa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "AUTH_FACTOR")
public class AuthFactor {
    @Id
    @Column(name = "AUTH_FACTOR_ID", length = 36)
    private String authFactorId;

    @Column(name = "USER_ID", nullable = false, unique = true, length = 36)
    private String userId;

    @Column(name = "FACTOR_TYPE", nullable = false, length = 30)
    private String factorType;

    @Column(name = "SECRET_ENCRYPTED", nullable = false, length = 1000)
    private String secretEncrypted;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Column(name = "VERIFIED", nullable = false)
    private boolean verified;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected AuthFactor() {
    }

    public AuthFactor(String userId, String secretEncrypted) {
        this.authFactorId = UUID.randomUUID().toString();
        this.userId = userId;
        this.factorType = "TOTP";
        this.secretEncrypted = secretEncrypted;
        this.createdAt = Instant.now();
    }

    public void replaceSecret(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public void enable() { this.enabled = true; this.verified = true; }
    public void disable() { this.enabled = false; this.verified = false; }
    public boolean isEnabled() { return enabled; }
    public String encryptedSecret() { return secretEncrypted; }
}
