package com.oracle.banking.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "APP_USER",
        indexes = @Index(name = "IX_APP_USER_ROLE", columnList = "ROLE_ID"))
public class AppUser {
    @Id
    @Column(name = "USER_ID", length = 36)
    private String userId;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "ROLE_ID",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_APP_USER_ROLE"))
    private Role role;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "EMAIL", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String phone;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String userId, Role role, String username, String email, String phone, String passwordHash) {
        this.userId = userId;
        this.role = role;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
