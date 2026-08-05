package com.oracle.banking.branch.controller;

import com.oracle.banking.branch.dto.BranchOperationsDtos.BranchPage;
import com.oracle.banking.branch.dto.BranchOperationsDtos.BranchSummary;
import com.oracle.banking.branch.service.BranchOperationsService;
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
@RequestMapping("/internal/operations/branches")
public class BranchOperationsController {
    private final BranchOperationsService service;
    private final String internalApiKey;

    public BranchOperationsController(
            BranchOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<BranchPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<BranchSummary> summary(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
