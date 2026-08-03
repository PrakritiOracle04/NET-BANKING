package com.oracle.banking.customer.controller;

import com.oracle.banking.customer.dto.CustomerDtos.Create;
import com.oracle.banking.customer.dto.CustomerDtos.OnboardingStatus;
import com.oracle.banking.customer.dto.CustomerDtos.Response;
import com.oracle.banking.customer.exception.CustomerExceptions.Unauthorized;
import com.oracle.banking.customer.service.CustomerService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
public class InternalCustomerController {
    private final CustomerService service;
    private final String internalApiKey;

    public InternalCustomerController(
            CustomerService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    ResponseEntity<Response> create(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @Valid @RequestBody Create request) {
        check(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{userId}/onboarding-status")
    OnboardingStatus onboardingStatus(
            @PathVariable String userId,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key) {
        check(key);
        return service.onboardingStatus(userId);
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new Unauthorized("Invalid internal API key");
        }
    }
}
