package com.oracle.banking.customer.controller;

import com.oracle.banking.customer.dto.CustomerDtos.KycResponse;
import com.oracle.banking.customer.dto.CustomerDtos.KycStatusUpdate;
import com.oracle.banking.customer.dto.CustomerDtos.KycSubmission;
import com.oracle.banking.customer.dto.CustomerDtos.Response;
import com.oracle.banking.customer.dto.CustomerDtos.Update;
import com.oracle.banking.customer.service.CustomerService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    ApiResponse<Response> me(Authentication authentication) {
        return ApiResponse.success("Customer profile", service.own(authentication.getName()));
    }

    @PutMapping("/me")
    ApiResponse<Response> update(Authentication authentication, @Valid @RequestBody Update request) {
        return ApiResponse.success("Customer profile updated", service.update(authentication.getName(), request));
    }

    @PutMapping("/me/kyc")
    ApiResponse<KycResponse> submitKyc(Authentication authentication, @Valid @RequestBody KycSubmission request) {
        return ApiResponse.success("KYC submitted", service.submitKyc(authentication.getName(), request));
    }

    @GetMapping("/me/kyc")
    ApiResponse<KycResponse> ownKyc(Authentication authentication) {
        return ApiResponse.success("KYC details", service.ownKyc(authentication.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<Response> byId(@PathVariable String id) {
        return ApiResponse.success("Customer profile", service.byId(id));
    }

    @PutMapping("/{userId}/kyc/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<KycResponse> updateKycStatus(
            @PathVariable String userId,
            @Valid @RequestBody KycStatusUpdate request) {
        return ApiResponse.success("KYC status updated", service.updateKycStatus(userId, request));
    }
}
