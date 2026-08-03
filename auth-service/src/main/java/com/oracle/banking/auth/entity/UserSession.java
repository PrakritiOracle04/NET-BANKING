package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "USER_SESSION",
        indexes = @Index(
                name = "IX_SESSION_USER_STATUS",
                columnList = "USER_ID, STATUS"))
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

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    protected UserSession() {
    }

    public UserSession(String userId, Instant expiresAt) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.loginAt = Instant.now();
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
    }

    public void invalidate() {
        this.status = "INVALIDATED";
    }
}
