package com.oracle.banking.scheduler.exception;

public final class SchedulerExceptions {
    private SchedulerExceptions() {}
    public static class BadRequest extends RuntimeException { public BadRequest(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
    public static class Forbidden extends RuntimeException { public Forbidden(String message) { super(message); } }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class DownstreamFailure extends RuntimeException { public DownstreamFailure(String message) { super(message); } }
}
