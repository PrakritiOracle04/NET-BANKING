package com.oracle.banking.workflow.controller;

import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawResponse;
import com.oracle.banking.workflow.service.BankingWorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banking")
public class BankingWorkflowController {
    private final BankingWorkflowService service;

    public BankingWorkflowController(BankingWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/transfer")
    ApiResponse<TransferResponse> transfer(Authentication authentication, @Valid @RequestBody TransferRequest request) {
        return ApiResponse.success("Transfer completed", service.transfer(authentication.getName(), isAdmin(authentication), request));
    }

    @PostMapping("/deposit")
    ApiResponse<DepositResponse> deposit(Authentication authentication, @Valid @RequestBody DepositRequest request) {
        return ApiResponse.success("Deposit completed", service.deposit(authentication.getName(), isAdmin(authentication), request));
    }

    @PostMapping("/withdraw")
    ApiResponse<WithdrawResponse> withdraw(Authentication authentication, @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("Withdrawal completed", service.withdraw(authentication.getName(), isAdmin(authentication), request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
