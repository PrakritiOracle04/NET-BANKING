package com.oracle.banking.twofa.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.oracle.banking.twofa.dto.TwoFactorDtos.SetupResponse;
import com.oracle.banking.twofa.dto.TwoFactorDtos.StatusResponse;
import com.oracle.banking.twofa.entity.AuthFactor;
import com.oracle.banking.twofa.exception.ResourceNotFoundException;
import com.oracle.banking.twofa.exception.TwoFactorException;
import com.oracle.banking.twofa.repository.AuthFactorRepository;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

@Service
public class TwoFactorService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final AuthFactorRepository factors;
    private final String issuer;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(AuthFactorRepository factors,
                            @Value("${twofa.issuer}") String issuer,
                            @Value("${twofa.encryption-key}") String encodedKey) {
        this.factors = factors;
        this.issuer = issuer;
        this.key = new SecretKeySpec(Decoders.BASE64.decode(encodedKey), "AES");
    }

    @Transactional
    public SetupResponse setup(String userId) {
        String secret = createSecret();
        AuthFactor factor = factors.findByUserId(userId)
                .orElseGet(() -> new AuthFactor(userId, encrypt(secret)));

        if (factors.findByUserId(userId).isPresent()) {
            factor.replaceSecret(encrypt(secret));
            factor.disable();
        }
        factors.save(factor);

        String uri = "otpauth://totp/" + encode(issuer) + ":" + encode(userId)
                + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
        return new SetupResponse(secret, uri, qr(uri), issuer, userId, false);
    }

    @Transactional
    public StatusResponse verifySetup(String userId, String code) {
        AuthFactor factor = required(userId);
        if (!valid(decrypt(factor.encryptedSecret()), code)) {
            throw new TwoFactorException("Invalid OTP code");
        }
        factor.enable();
        return new StatusResponse(true);
    }

    public StatusResponse verify(String userId, String code) {
        AuthFactor factor = required(userId);
        if (!factor.isEnabled() || !valid(decrypt(factor.encryptedSecret()), code)) {
            throw new TwoFactorException("Invalid OTP code");
        }
        return new StatusResponse(true);
    }

    @Transactional
    public StatusResponse disable(String userId, String code) {
        AuthFactor factor = required(userId);
        if (!factor.isEnabled() || !valid(decrypt(factor.encryptedSecret()), code)) {
            throw new TwoFactorException("Invalid OTP code");
        }
        factor.disable();
        return new StatusResponse(false);
    }

    public StatusResponse status(String userId) {
        return new StatusResponse(factors.findByUserId(userId).map(AuthFactor::isEnabled).orElse(false));
    }

    private AuthFactor required(String userId) {
        return factors.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("2FA setup not found"));
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = Arrays.copyOf(iv, iv.length + encrypted.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect TOTP secret", exception);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read TOTP secret", exception);
        }
    }

    private String createSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    private boolean valid(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long counter = Instant.now().getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (code.equals(code(secret, counter + offset))) return true;
        }
        return false;
    }

    private String code(String secret, long counter) {
        try {
            byte[] counterBytes = new byte[8];
            for (int index = 7; index >= 0; index--) {
                counterBytes[index] = (byte) counter;
                counter >>>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate TOTP", exception);
        }
    }

    private String qr(String uri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate QR code", exception);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String base32Encode(byte[] data) {
        StringBuilder output = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                output.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) output.append(BASE32[(buffer << (5 - bits)) & 31]);
        return output.toString();
    }

    private static byte[] base32Decode(String value) {
        int buffer = 0;
        int bits = 0;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (char character : value.replace("=", "").toUpperCase().toCharArray()) {
            int current = new String(BASE32).indexOf(character);
            if (current < 0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer = (buffer << 5) | current;
            bits += 5;
            if (bits >= 8) {
                output.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return output.toByteArray();
    }
}
