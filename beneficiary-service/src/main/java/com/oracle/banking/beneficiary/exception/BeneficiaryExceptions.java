package com.oracle.banking.beneficiary.exception;

public final class BeneficiaryExceptions {
    private BeneficiaryExceptions() {}

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
