package com.oracle.banking.auth;

import java.security.SecureRandom;
import java.util.Base64;

public final class TestSecrets {
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TestSecrets() {
    }

    public static String randomBase64Key() {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
