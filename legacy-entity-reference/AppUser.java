package com.oracle.banking.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "APP_USER")
public class AppUser implements Serializable {

    @Id
    @Column(name = "USER_ID")
    private String userId;

    @ManyToOne
    @JoinColumn(name = "ROLE_ID")
    private Role role;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "PASSWORD_HASH")
    private String passwordHash;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "CREATED_AT")
    private Timestamp createdAt;

    @Column(name = "DOB")
    private Date dob;

    @Column(name = "KYC_STATUS")
    private String kycStatus;

    @Column(name = "PROFILE_STATUS")
    private String profileStatus;

    public AppUser() {
    }

    // We'll generate getters and setters after all classes are created.
}