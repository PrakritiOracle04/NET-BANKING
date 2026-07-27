package com.oracle.banking.shared.response;

import java.time.Instant;

public record ErrorResponse(boolean success, String message, String path, Instant timestamp) {
    public static ErrorResponse of(String message, String path) {
        return new ErrorResponse(false, message, path, Instant.now());
    }
}
