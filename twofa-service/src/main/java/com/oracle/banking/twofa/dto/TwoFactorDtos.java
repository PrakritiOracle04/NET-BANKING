package com.oracle.banking.twofa.dto;
import jakarta.validation.constraints.NotBlank;
public final class TwoFactorDtos {
    private TwoFactorDtos() { }
    public record SetupResponse(String secret, String otpauthUri, String qrCodeBase64, String issuer, String accountName, boolean enabled) { }
    public record VerifyRequest(@NotBlank String otpCode) { }
    public record StatusResponse(boolean enabled) { }
    public record InternalVerification(@NotBlank String userId, @NotBlank String otpCode) { }
}
