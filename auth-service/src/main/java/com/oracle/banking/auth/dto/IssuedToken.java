package com.oracle.banking.auth.dto;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
