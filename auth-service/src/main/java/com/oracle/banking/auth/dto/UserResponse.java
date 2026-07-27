package com.oracle.banking.auth.dto;

public record UserResponse(String userId, String username, String email, String role) {
}
