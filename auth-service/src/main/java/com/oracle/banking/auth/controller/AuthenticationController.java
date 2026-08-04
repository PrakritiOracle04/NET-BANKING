package com.oracle.banking.auth.controller;

import com.oracle.banking.auth.dto.LoginRequest;
import com.oracle.banking.auth.dto.LoginResponse;
import com.oracle.banking.auth.dto.RegisterRequest;
import com.oracle.banking.auth.dto.RegisterResponse;
import com.oracle.banking.auth.dto.UserResponse;
import com.oracle.banking.auth.service.AuthenticationService;
import com.oracle.banking.auth.security.SessionPrincipal;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService service;

    public AuthenticationController(AuthenticationService service) {
        this.service = service;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered", service.register(request)));
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Login successful", service.login(request)));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        service.logout(principal.userId(), principal.sessionId());
        return ApiResponse.success("Logout successful", null);
    }

    @PostMapping("/logout-all")
    ApiResponse<Void> logoutAll(Authentication authentication) {
        SessionPrincipal principal = principal(authentication);
        service.logoutAll(principal.userId());
        return ApiResponse.success("All sessions logged out", null);
    }

    @GetMapping("/me")
    ApiResponse<UserResponse> me(Authentication authentication) {
        return ApiResponse.success("Authenticated user", service.currentUser(authentication.getName()));
    }

    private SessionPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new com.oracle.banking.auth.exception.SessionAuthenticationException("Session is invalid or expired");
        }
        return principal;
    }
}
