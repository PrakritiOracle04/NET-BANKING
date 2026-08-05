package com.oracle.banking.billpayment.controller;

import com.oracle.banking.billpayment.dto.BillPaymentOperationsDtos.BillPaymentPage;
import com.oracle.banking.billpayment.dto.BillPaymentOperationsDtos.BillPaymentSummary;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import com.oracle.banking.billpayment.service.BillPaymentOperationsService;
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
@RequestMapping("/internal/operations/bill-payments")
public class BillPaymentOperationsController {
    private final BillPaymentOperationsService service;
    private final String internalApiKey;

    public BillPaymentOperationsController(
            BillPaymentOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<BillPaymentPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) BillPaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(customerUserId, status, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<BillPaymentSummary> summary(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
