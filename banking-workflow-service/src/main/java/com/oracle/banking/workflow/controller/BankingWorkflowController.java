package com.oracle.banking.workflow.controller;

import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.OpenAccountRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.OpenAccountResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.BillPaymentWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.BillPaymentWorkflowResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanRepaymentWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanRepaymentWorkflowResponse;
import com.oracle.banking.workflow.service.BankingWorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/banking")
public class BankingWorkflowController {
    private final BankingWorkflowService service;

    public BankingWorkflowController(BankingWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/accounts/open")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<OpenAccountResponse> openAccount(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OpenAccountRequest request) {
        return ApiResponse.success(
                "Account opened",
                service.openAccount(authentication.getName(), idempotencyKey, request));
    }

    @PostMapping("/transfer")
    ApiResponse<TransferResponse> transfer(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody TransferRequest request) {
        return ApiResponse.success("Transfer completed", service.transfer(authentication.getName(), isAdmin(authentication), idempotencyKey, request));
    }

    @PostMapping("/bill-payments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<BillPaymentWorkflowResponse> payBill(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BillPaymentWorkflowRequest request) {
        return ApiResponse.success(
                "Bill payment completed",
                service.payBill(authentication.getName(), idempotencyKey, request));
    }

    @PostMapping("/loans/{loanId}/repay")
    ApiResponse<LoanRepaymentWorkflowResponse> repayLoan(
            Authentication authentication,
            @PathVariable String loanId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LoanRepaymentWorkflowRequest request) {
        return ApiResponse.success(
                "Loan repayment completed",
                service.repayLoan(authentication.getName(), idempotencyKey, loanId, request));
    }

    @PostMapping("/deposit")
    ApiResponse<DepositResponse> deposit(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody DepositRequest request) {
        return ApiResponse.success("Deposit completed", service.deposit(authentication.getName(), isAdmin(authentication), idempotencyKey, request));
    }

    @PostMapping("/withdraw")
    ApiResponse<WithdrawResponse> withdraw(Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("Withdrawal completed", service.withdraw(authentication.getName(), isAdmin(authentication), idempotencyKey, request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
