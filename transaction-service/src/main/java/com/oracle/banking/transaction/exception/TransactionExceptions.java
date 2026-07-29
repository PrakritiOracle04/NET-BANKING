package com.oracle.banking.transaction.exception;

public final class TransactionExceptions {
    private TransactionExceptions() {}

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Duplicate extends RuntimeException {
        public Duplicate(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }
}
