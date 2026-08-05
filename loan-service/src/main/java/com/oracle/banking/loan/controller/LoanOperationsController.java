package com.oracle.banking.loan.controller;

import com.oracle.banking.loan.dto.LoanOperationsDtos.LoanPage;
import com.oracle.banking.loan.dto.LoanOperationsDtos.LoanSummary;
import com.oracle.banking.loan.entity.LoanStatus;
import com.oracle.banking.loan.entity.LoanType;
import com.oracle.banking.loan.service.LoanOperationsService;
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
@RequestMapping("/internal/operations/loans")
public class LoanOperationsController {
    private final LoanOperationsService service;
    private final String internalApiKey;

    public LoanOperationsController(
            LoanOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<LoanPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) LoanType loanType,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(customerUserId, loanType, status, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<LoanSummary> summary(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
