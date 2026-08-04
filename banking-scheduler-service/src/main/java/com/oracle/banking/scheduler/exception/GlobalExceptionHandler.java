package com.oracle.banking.scheduler.exception;

import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.BadRequest;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.Conflict;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.DownstreamFailure;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.Forbidden;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.NotFound;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadRequest.class)
    ResponseEntity<ErrorResponse> badRequest(RuntimeException ex, HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request); }

    @ExceptionHandler(NotFound.class)
    ResponseEntity<ErrorResponse> notFound(RuntimeException ex, HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, ex.getMessage(), request); }

    @ExceptionHandler({Forbidden.class, MissingRequestHeaderException.class})
    ResponseEntity<ErrorResponse> forbidden(Exception ex, HttpServletRequest request) { return error(HttpStatus.FORBIDDEN, "Access denied", request); }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<ErrorResponse> conflict(RuntimeException ex, HttpServletRequest request) { return error(HttpStatus.CONFLICT, ex.getMessage(), request); }

    @ExceptionHandler(DownstreamFailure.class)
    ResponseEntity<ErrorResponse> downstream(RuntimeException ex, HttpServletRequest request) { return error(HttpStatus.BAD_GATEWAY, ex.getMessage(), request); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, DateTimeException.class})
    ResponseEntity<ErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
    }
}
