package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
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
import org.hibernate.annotations.Check;

@Entity
@Check(constraints = "FAILED_ATTEMPTS >= 0")
@Table(
        name = "PASSWORD_RESET_CHALLENGES",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PASSWORD_RESET_TOKEN_DIGEST",
                columnNames = "RESET_TOKEN_DIGEST"),
        indexes = {
            @Index(name = "IX_PASSWORD_RESET_USER_STATUS", columnList = "USER_ID, STATUS, CREATED_AT"),
            @Index(name = "IX_PASSWORD_RESET_OTP_EXPIRY", columnList = "STATUS, OTP_EXPIRES_AT"),
            @Index(name = "IX_PASSWORD_RESET_TOKEN_EXPIRY", columnList = "STATUS, TOKEN_EXPIRES_AT")
        })
public class PasswordResetChallenge {
    @Id
    @Column(name = "CHALLENGE_ID", length = 36)
    private String challengeId;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "USER_ID",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_PASSWORD_RESET_APP_USER"))
    private AppUser user;

    @Column(name = "OTP_DIGEST", nullable = false, length = 128)
    private String otpDigest;

    @Column(name = "RESET_TOKEN_DIGEST", unique = true, length = 128)
    private String resetTokenDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private PasswordResetStatus status;

    @Column(name = "FAILED_ATTEMPTS", nullable = false)
    private int failedAttempts;

    @Column(name = "OTP_EXPIRES_AT", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "TOKEN_EXPIRES_AT")
    private Instant tokenExpiresAt;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "LAST_SENT_AT", nullable = false)
    private Instant lastSentAt;

    @Column(name = "VERIFIED_AT")
    private Instant verifiedAt;

    @Column(name = "CONSUMED_AT")
    private Instant consumedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected PasswordResetChallenge() {
    }

    public PasswordResetChallenge(AppUser user, String otpDigest, Instant otpExpiresAt, Instant lastSentAt) {
        this.challengeId = UUID.randomUUID().toString();
        this.user = user;
        this.otpDigest = otpDigest;
        this.status = PasswordResetStatus.PENDING;
        this.failedAttempts = 0;
        this.otpExpiresAt = otpExpiresAt;
        this.lastSentAt = lastSentAt;
    }

    @PrePersist
    void beforeCreate() {
        if (challengeId == null) challengeId = UUID.randomUUID().toString();
        if (status == null) status = PasswordResetStatus.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void expire() { status = PasswordResetStatus.EXPIRED; }
    public void deliveryFailed() { status = PasswordResetStatus.DELIVERY_FAILED; }
    public void failedAttempt(int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) status = PasswordResetStatus.LOCKED;
    }
    public void verify(String resetTokenDigest, Instant tokenExpiresAt) {
        this.resetTokenDigest = resetTokenDigest;
        this.tokenExpiresAt = tokenExpiresAt;
        this.verifiedAt = Instant.now();
        this.status = PasswordResetStatus.VERIFIED;
    }
    public void consume() {
        this.consumedAt = Instant.now();
        this.status = PasswordResetStatus.CONSUMED;
    }

    public String getChallengeId() { return challengeId; }
    public AppUser getUser() { return user; }
    public String getOtpDigest() { return otpDigest; }
    public String getResetTokenDigest() { return resetTokenDigest; }
    public PasswordResetStatus getStatus() { return status; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSentAt() { return lastSentAt; }
}
