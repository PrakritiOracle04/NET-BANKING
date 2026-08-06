package com.oracle.banking.auth.entity;

public enum PasswordResetStatus {
    PENDING,
    VERIFIED,
    CONSUMED,
    EXPIRED,
    LOCKED,
    DELIVERY_FAILED
}
