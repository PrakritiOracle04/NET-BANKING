package com.oracle.banking.twofa.exception;
import com.oracle.banking.shared.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<ErrorResponse> missing(ResourceNotFoundException e,HttpServletRequest r){return error(HttpStatus.NOT_FOUND,e.getMessage(),r);}
 @ExceptionHandler({TwoFactorException.class,UnauthorizedException.class}) ResponseEntity<ErrorResponse> bad(RuntimeException e,HttpServletRequest r){return error(e instanceof UnauthorizedException?HttpStatus.UNAUTHORIZED:HttpStatus.BAD_REQUEST,e.getMessage(),r);}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> generic(Exception e,HttpServletRequest r){return error(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred",r);}
 private ResponseEntity<ErrorResponse> error(HttpStatus s,String m,HttpServletRequest r){return ResponseEntity.status(s).body(ErrorResponse.of(m,r.getRequestURI()));}
}
