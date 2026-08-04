package com.oracle.banking.workflow.controller;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.BillPaymentWorkflowResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanMaintenanceResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanMaintenanceWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.ScheduledBillPaymentWorkflowRequest;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Forbidden;
import com.oracle.banking.workflow.service.BankingWorkflowService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/workflows")
public class InternalWorkflowController {
    private final BankingWorkflowService service;
    private final String internalApiKey;

    public InternalWorkflowController(BankingWorkflowService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/scheduled-bill-payments")
    ApiResponse<BillPaymentWorkflowResponse> scheduledBillPayment(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @Valid @RequestBody ScheduledBillPaymentWorkflowRequest request) {
        check(suppliedKey);
        return ApiResponse.success("Scheduled bill payment completed", service.payScheduledBill(request));
    }

    @PostMapping("/loan-maintenance")
    ApiResponse<LoanMaintenanceResponse> loanMaintenance(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @Valid @RequestBody LoanMaintenanceWorkflowRequest request) {
        check(suppliedKey);
        return ApiResponse.success("Loan maintenance completed", service.runLoanMaintenance(request));
    }

    private void check(String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) throw new Forbidden("Invalid internal API key");
    }
}
