package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "USER_SESSION")
public class UserSession {
    @Id
    @Column(name = "SESSION_ID", length = 36)
    private String sessionId;

    @Column(name = "USER_ID", nullable = false, length = 36)
    private String userId;

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
