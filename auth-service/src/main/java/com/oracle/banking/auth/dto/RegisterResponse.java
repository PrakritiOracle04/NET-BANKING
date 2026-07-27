package com.oracle.banking.auth.dto;

public record RegisterResponse(String userId, String username, String email, String role, boolean twoFactorEnabled) {
}
