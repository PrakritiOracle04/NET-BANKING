package com.oracle.banking.account.exception;

public final class AccountExceptions {
    private AccountExceptions() {}

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Duplicate extends RuntimeException {
        public Duplicate(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }

    public static class InsufficientBalance extends RuntimeException {
        public InsufficientBalance(String message) { super(message); }
    }
}
