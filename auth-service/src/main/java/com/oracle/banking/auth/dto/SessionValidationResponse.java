package com.oracle.banking.auth.dto;

import java.time.Instant;
import java.util.List;

public record SessionValidationResponse(
        boolean valid,
        String sessionId,
        String userId,
        List<String> roles,
        Instant expiresAt) {
}
