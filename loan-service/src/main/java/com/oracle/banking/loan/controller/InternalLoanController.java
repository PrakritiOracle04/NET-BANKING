package com.oracle.banking.loan.controller;

import com.oracle.banking.loan.dto.LoanDtos.InternalCompleteLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalCreateLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalFailLoanRepaymentRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanMaintenanceRequest;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanMaintenanceResponse;
import com.oracle.banking.loan.dto.LoanDtos.InternalLoanValidationResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanRepaymentResponse;
import com.oracle.banking.loan.exception.LoanExceptions.Forbidden;
import com.oracle.banking.loan.service.LoanService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalLoanController {
    private final LoanService service;
    private final String internalApiKey;

    public InternalLoanController(LoanService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/loans/{id}/validate")
    InternalLoanValidationResponse validate(
            @PathVariable String id,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.validate(id, customerUserId, amount);
    }

    @PostMapping("/loan-repayments")
    LoanRepaymentResponse create(
            @Valid @RequestBody InternalCreateLoanRepaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.createPending(request);
    }

    @PutMapping("/loan-repayments/{id}/complete")
    LoanRepaymentResponse complete(
            @PathVariable String id,
            @Valid @RequestBody InternalCompleteLoanRepaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.complete(id, request);
    }

    @PutMapping("/loan-repayments/{id}/fail")
    LoanRepaymentResponse fail(
            @PathVariable String id,
            @RequestBody InternalFailLoanRepaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.fail(id, request == null ? new InternalFailLoanRepaymentRequest(null) : request);
    }

    @PutMapping("/loan-repayments/{id}/reverse")
    LoanRepaymentResponse reverse(
            @PathVariable String id,
            @RequestBody InternalFailLoanRepaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.reverse(id, request == null ? new InternalFailLoanRepaymentRequest(null) : request);
    }

    @PutMapping("/loan-repayments/workflow/{reference}/reverse")
    LoanRepaymentResponse reverseByWorkflowReference(
            @PathVariable String reference,
            @RequestBody InternalFailLoanRepaymentRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.reverseByWorkflowReference(reference, request == null ? new InternalFailLoanRepaymentRequest(null) : request);
    }

    @PostMapping("/loans/maintenance/emi-reminders")
    InternalLoanMaintenanceResponse emiReminders(
            @Valid @RequestBody InternalLoanMaintenanceRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.sendEmiReminders(request);
    }

    @PostMapping("/loans/maintenance/overdue")
    InternalLoanMaintenanceResponse overdue(
            @Valid @RequestBody InternalLoanMaintenanceRequest request,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        check(suppliedKey);
        return service.markOverdue(request);
    }

    private void check(String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) throw new Forbidden("Invalid internal API key");
    }
}
