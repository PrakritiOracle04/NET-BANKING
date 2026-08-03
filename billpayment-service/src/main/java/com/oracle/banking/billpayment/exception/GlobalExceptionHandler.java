package com.oracle.banking.billpayment.exception;

import com.oracle.banking.billpayment.exception.BillPaymentExceptions.BadRequest;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.Conflict;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.Forbidden;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.NotFound;
import com.oracle.banking.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFound.class)
    ResponseEntity<ErrorResponse> notFound(NotFound ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({BadRequest.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof BadRequest ? ex.getMessage() : "Invalid request";
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<ErrorResponse> conflict(Conflict ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({Forbidden.class, AccessDeniedException.class})
    ResponseEntity<ErrorResponse> forbidden(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> general(Exception ex, HttpServletRequest request) {
        log.error("Unexpected Bill Payment Service error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
    }
}
