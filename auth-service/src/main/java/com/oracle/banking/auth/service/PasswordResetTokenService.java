package com.oracle.banking.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public PasswordResetTokenService(@Value("${password-reset.otp-hmac-key}") String configuredKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException exception) {
            keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("PASSWORD_RESET_OTP_HMAC_KEY must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    public String otp() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    public String resetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String digest(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to digest password reset secret", exception);
        }
    }

    public boolean matches(String submittedValue, String expectedDigest) {
        return MessageDigest.isEqual(
                digest(submittedValue).getBytes(StandardCharsets.UTF_8),
                expectedDigest.getBytes(StandardCharsets.UTF_8));
    }
}
