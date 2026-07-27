package com.oracle.banking.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt, String username, String role,
                            boolean twoFactorEnabled) {
}
