package com.oracle.banking.auth.controller;

import com.oracle.banking.auth.dto.UserOperationsDtos.UserPage;
import com.oracle.banking.auth.dto.UserOperationsDtos.UserSummary;
import com.oracle.banking.auth.service.UserOperationsService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/operations/users")
public class UserOperationsController {
    private final UserOperationsService service;
    private final String internalApiKey;

    public UserOperationsController(
            UserOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<UserPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(status, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<UserSummary> summary(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
