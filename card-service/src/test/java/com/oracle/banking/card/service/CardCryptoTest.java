package com.oracle.banking.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CardCryptoTest {
    private static final String KEY = randomKey();

    @Test
    void encryptsRandomlyAndFingerprintsDeterministically() {
        CardCrypto crypto = new CardCrypto(KEY);
        String cardNumber = "4532123456789012";

        String firstCiphertext = crypto.encrypt(cardNumber);
        String secondCiphertext = crypto.encrypt(cardNumber);

        assertThat(firstCiphertext).doesNotContain(cardNumber).isNotEqualTo(secondCiphertext);
        assertThat(crypto.fingerprint(cardNumber)).isEqualTo(crypto.fingerprint(cardNumber));
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
