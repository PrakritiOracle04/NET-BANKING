package com.oracle.banking.customer.exception;

public final class CustomerExceptions {
    private CustomerExceptions() {}

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Duplicate extends RuntimeException {
        public Duplicate(String message) { super(message); }
    }

    public static class Unauthorized extends RuntimeException {
        public Unauthorized(String message) { super(message); }
    }

    public static class StorageFailure extends RuntimeException {
        public StorageFailure(String message, Throwable cause) { super(message, cause); }
    }
}
