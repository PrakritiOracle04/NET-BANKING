package com.oracle.banking.loan.exception;

public final class LoanExceptions {
    private LoanExceptions() {}

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }
}
