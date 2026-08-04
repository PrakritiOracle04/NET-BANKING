package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "USER_SESSION",
        indexes = {
            @Index(name = "IX_SESSION_USER_STATUS", columnList = "USER_ID, STATUS"),
            @Index(name = "IX_SESSION_STATUS_EXPIRY", columnList = "STATUS, EXPIRES_AT")
        })
public class UserSession {
    @Id
    @Column(name = "SESSION_ID", length = 36)
    private String sessionId;

    @Column(name = "USER_ID", nullable = false, length = 36)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "USER_ID",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_SESSION_USER"))
    private AppUser user;

    @Column(name = "LOGIN_AT", nullable = false)
    private Instant loginAt;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "INVALIDATED_AT")
    private Instant invalidatedAt;

    protected UserSession() {
    }

    public UserSession(String sessionId, String userId, Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.loginAt = Instant.now();
        this.expiresAt = expiresAt;
        this.status = SessionStatus.ACTIVE;
    }

    public void invalidate() {
        if (status == SessionStatus.ACTIVE) {
            status = SessionStatus.INVALIDATED;
            invalidatedAt = Instant.now();
        }
    }

    public void expire() {
        if (status == SessionStatus.ACTIVE) status = SessionStatus.EXPIRED;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public Instant getLoginAt() { return loginAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public SessionStatus getStatus() { return status; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
}
