package com.oracle.banking.customer.exception;

import com.oracle.banking.customer.exception.CustomerExceptions.BadRequest;
import com.oracle.banking.customer.exception.CustomerExceptions.Duplicate;
import com.oracle.banking.customer.exception.CustomerExceptions.NotFound;
import com.oracle.banking.customer.exception.CustomerExceptions.Unauthorized;
import com.oracle.banking.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({BadRequest.class, MethodArgumentNotValidException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(NotFound.class)
    ResponseEntity<ErrorResponse> notFound(NotFound exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(Duplicate.class)
    ResponseEntity<ErrorResponse> duplicate(Duplicate exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(Unauthorized.class)
    ResponseEntity<ErrorResponse> unauthorized(Unauthorized exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
    }
}
