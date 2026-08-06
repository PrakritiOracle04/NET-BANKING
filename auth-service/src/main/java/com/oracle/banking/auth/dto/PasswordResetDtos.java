package com.oracle.banking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public final class PasswordResetDtos {
    private PasswordResetDtos() {}

    public record PasswordResetRequest(@Email @NotBlank String email) {}

    public record PasswordResetVerifyRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otpCode) {}

    public record PasswordResetVerifyResponse(String resetToken, Instant expiresAt) {}

    public record PasswordResetConfirmRequest(
            @NotBlank String resetToken,
            @NotBlank String newPassword,
            @NotBlank String confirmPassword) {}
}
