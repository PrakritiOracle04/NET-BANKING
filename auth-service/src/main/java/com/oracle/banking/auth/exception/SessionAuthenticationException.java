package com.oracle.banking.auth.exception;

public class SessionAuthenticationException extends RuntimeException {
    public SessionAuthenticationException(String message) {
        super(message);
    }
}
