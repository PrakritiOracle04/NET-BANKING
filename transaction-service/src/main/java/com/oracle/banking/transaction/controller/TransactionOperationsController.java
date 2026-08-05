package com.oracle.banking.transaction.controller;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.transaction.dto.TransactionOperationsDtos.TransactionPage;
import com.oracle.banking.transaction.dto.TransactionOperationsDtos.TransactionSummary;
import com.oracle.banking.transaction.entity.TransactionStatus;
import com.oracle.banking.transaction.entity.TransactionType;
import com.oracle.banking.transaction.service.TransactionOperationsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/internal/operations/transactions")
public class TransactionOperationsController {
    private final TransactionOperationsService service;
    private final String internalApiKey;

    public TransactionOperationsController(
            TransactionOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping({"/search", "/statement"})
    ResponseEntity<TransactionPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(
                customerUserId, accountId, transactionType, status, fromDate, toDate, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<TransactionSummary> summary(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
