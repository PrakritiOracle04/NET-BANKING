package com.oracle.banking.auth.exception;

import com.oracle.banking.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ErrorResponse> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({BadRequestException.class, TwoFactorException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({BadCredentialsException.class, SessionAuthenticationException.class})
    ResponseEntity<ErrorResponse> badCredentials(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> missing(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
    }
}
