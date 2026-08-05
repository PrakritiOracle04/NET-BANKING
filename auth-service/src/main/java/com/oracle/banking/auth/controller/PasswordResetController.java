package com.oracle.banking.auth.controller;

import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetConfirmRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetVerifyRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetVerifyResponse;
import com.oracle.banking.auth.service.PasswordResetService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/request")
    ResponseEntity<ApiResponse<Void>> request(@Valid @RequestBody PasswordResetRequest request) {
        String message = service.request(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(message, null));
    }

    @PostMapping("/verify")
    ResponseEntity<ApiResponse<PasswordResetVerifyResponse>> verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Password reset code verified", service.verify(request)));
    }

    @PostMapping("/confirm")
    ResponseEntity<ApiResponse<Void>> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        service.confirm(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Password reset successful. Please log in again", null));
    }
}
