package com.oracle.banking.card.exception;

public final class CardExceptions {
    private CardExceptions() {}

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class DownstreamFailure extends RuntimeException {
        public DownstreamFailure(String message) { super(message); }
    }
}
