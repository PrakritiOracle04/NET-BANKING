package com.oracle.banking.auth.dto;

public record CustomerProfileCreate(String userId, String fullName, String email, String phone) {
}
