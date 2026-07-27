package com.oracle.banking.auth.exception;

public class TwoFactorException extends RuntimeException {
    public TwoFactorException(String message) { super(message); }
}
