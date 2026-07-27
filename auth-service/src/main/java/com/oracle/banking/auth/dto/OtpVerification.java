package com.oracle.banking.auth.dto;

public record OtpVerification(String userId, String otpCode) {
}
