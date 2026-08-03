package com.oracle.banking.card.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CardCrypto {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hashingKey;

    public CardCrypto(@Value("${card.encryption-key}") String encodedKey) {
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) throw new IllegalArgumentException("CARD_ENCRYPTION_KEY must decode to exactly 32 bytes");
        encryptionKey = new SecretKeySpec(key, "AES");
        hashingKey = new SecretKeySpec(deriveHashKey(key), "HmacSHA256");
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt card number", exception);
        }
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashingKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to fingerprint card number", exception);
        }
    }

    private byte[] deriveHashKey(byte[] masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(masterKey);
            digest.update("bank-card-fingerprint".getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive card fingerprint key", exception);
        }
    }
}
