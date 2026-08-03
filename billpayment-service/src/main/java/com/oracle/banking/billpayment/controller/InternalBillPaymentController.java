package com.oracle.banking.billpayment.controller;

import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillPaymentResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalBillerValidationResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalCompleteBillPaymentRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalCreateBillPaymentRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.InternalFailBillPaymentRequest;
import com.oracle.banking.billpayment.exception.BillPaymentExceptions.Forbidden;
import com.oracle.banking.billpayment.service.BillPaymentService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalBillPaymentController {
    private final BillPaymentService service;
    private final String internalApiKey;

    public InternalBillPaymentController(
            BillPaymentService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/billers/{id}/validate")
    InternalBillerValidationResponse validate(
            @PathVariable String id,
            @RequestParam String customerUserId,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.validateBiller(id, customerUserId);
    }

    @PostMapping("/bill-payments")
    @ResponseStatus(HttpStatus.CREATED)
    BillPaymentResponse create(
            @Valid @RequestBody InternalCreateBillPaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.createPending(request);
    }

    @PutMapping("/bill-payments/{id}/complete")
    BillPaymentResponse complete(
            @PathVariable String id,
            @Valid @RequestBody InternalCompleteBillPaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.complete(id, request);
    }

    @PutMapping("/bill-payments/{id}/fail")
    BillPaymentResponse fail(
            @PathVariable String id,
            @Valid @RequestBody InternalFailBillPaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.fail(id, request);
    }

    @PutMapping("/bill-payments/{id}/cancel")
    BillPaymentResponse cancel(
            @PathVariable String id,
            @Valid @RequestBody InternalFailBillPaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.cancel(id, request);
    }

    @PutMapping("/bill-payments/workflow/{reference}/cancel")
    BillPaymentResponse cancelByWorkflowReference(
            @PathVariable String reference,
            @Valid @RequestBody InternalFailBillPaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        requireKey(suppliedKey);
        return service.cancelByWorkflowReference(reference, request);
    }

    private void requireKey(String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) throw new Forbidden("Invalid internal API key");
    }
}
