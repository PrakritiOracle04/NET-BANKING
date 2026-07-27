package com.oracle.banking.shared.validation;

public final class PasswordPolicy {
    private static final String COMPLEX_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(COMPLEX_PASSWORD);
    }
}
