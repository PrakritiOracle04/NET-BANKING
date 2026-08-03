package com.oracle.banking.workflow.exception;

import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.workflow.exception.WorkflowExceptions.BadRequest;
import com.oracle.banking.workflow.exception.WorkflowExceptions.DownstreamFailure;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Forbidden;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Conflict;
import com.oracle.banking.workflow.exception.WorkflowExceptions.CompensationPending;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BadRequest.class)
    ResponseEntity<ErrorResponse> badRequest(BadRequest ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({Forbidden.class, AccessDeniedException.class})
    ResponseEntity<ErrorResponse> forbidden(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(DownstreamFailure.class)
    ResponseEntity<ErrorResponse> downstream(DownstreamFailure ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<ErrorResponse> conflict(Conflict ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(CompensationPending.class)
    ResponseEntity<ErrorResponse> compensationPending(CompensationPending ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, MissingRequestHeaderException.class})
    ResponseEntity<ErrorResponse> validation(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> general(Exception ex, HttpServletRequest request) {
        log.error("Unexpected Banking Workflow Service error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
    }
}
