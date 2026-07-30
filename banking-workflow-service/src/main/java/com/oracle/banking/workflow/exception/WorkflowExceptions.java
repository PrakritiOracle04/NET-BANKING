package com.oracle.banking.workflow.exception;

public final class WorkflowExceptions {
    private WorkflowExceptions() {}

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }

    public static class DownstreamFailure extends RuntimeException {
        public DownstreamFailure(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class CompensationPending extends RuntimeException {
        public CompensationPending(String message) { super(message); }
    }
}
