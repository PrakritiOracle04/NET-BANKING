package com.oracle.banking.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardCryptoTest {
    private static final String KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void encryptsRandomlyAndFingerprintsDeterministically() {
        CardCrypto crypto = new CardCrypto(KEY);
        String cardNumber = "4532123456789012";

        String firstCiphertext = crypto.encrypt(cardNumber);
        String secondCiphertext = crypto.encrypt(cardNumber);

        assertThat(firstCiphertext).doesNotContain(cardNumber).isNotEqualTo(secondCiphertext);
        assertThat(crypto.fingerprint(cardNumber)).isEqualTo(crypto.fingerprint(cardNumber));
    }
}
