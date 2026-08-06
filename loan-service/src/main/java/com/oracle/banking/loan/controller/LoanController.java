package com.oracle.banking.loan.controller;

import com.oracle.banking.loan.dto.LoanDtos.CalculateEmiRequest;
import com.oracle.banking.loan.dto.LoanDtos.EmiCalculationResponse;
import com.oracle.banking.loan.dto.LoanDtos.EmiScheduleResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanBalanceResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationApprovalRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationRejectionRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationRequest;
import com.oracle.banking.loan.dto.LoanDtos.LoanApplicationResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanDetailsResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanRepaymentResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanSummaryResponse;
import com.oracle.banking.loan.dto.LoanDtos.LoanTypeOption;
import com.oracle.banking.loan.dto.LoanDtos.RegisterLoanRequest;
import com.oracle.banking.loan.dto.LoanDtos.UpdateLoanStatusRequest;
import com.oracle.banking.loan.entity.LoanApplicationStatus;
import com.oracle.banking.loan.entity.LoanStatus;
import com.oracle.banking.loan.entity.LoanType;
import com.oracle.banking.loan.service.LoanApplicationService;
import com.oracle.banking.loan.service.LoanService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {
    private final LoanService service;
    private final LoanApplicationService applicationService;

    public LoanController(LoanService service, LoanApplicationService applicationService) {
        this.service = service;
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<LoanDetailsResponse> register(@Valid @RequestBody RegisterLoanRequest request) {
        return ApiResponse.success("Loan registered", service.register(request));
    }

    @GetMapping
    ApiResponse<List<LoanSummaryResponse>> loans(
            Authentication authentication,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) LoanStatus status) {
        return ApiResponse.success("Loans", service.loans(
                authentication.getName(),
                isAdmin(authentication),
                customerUserId,
                status));
    }

    @GetMapping("/types")
    ApiResponse<List<LoanTypeOption>> loanTypes() {
        return ApiResponse.success("Loan types", service.loanTypes());
    }

    @PostMapping("/applications")
    ApiResponse<LoanApplicationResponse> apply(
            Authentication authentication,
            @Valid @RequestBody LoanApplicationRequest request) {
        return ApiResponse.success("Loan application submitted", applicationService.apply(authentication.getName(), request));
    }

    @GetMapping("/applications")
    ApiResponse<List<LoanApplicationResponse>> myApplications(Authentication authentication) {
        return ApiResponse.success("Loan applications", applicationService.myApplications(authentication.getName()));
    }

    @GetMapping("/applications/{applicationId}")
    ApiResponse<LoanApplicationResponse> application(@PathVariable String applicationId, Authentication authentication) {
        return ApiResponse.success("Loan application", applicationService.application(
                applicationId, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<LoanApplicationResponse>> searchApplications(
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(required = false) LoanType loanType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Loan applications", applicationService.search(customerUserId, status, loanType, page, size));
    }

    @PostMapping("/admin/applications/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<LoanApplicationResponse> approveApplication(
            @PathVariable String applicationId,
            Authentication authentication,
            @Valid @RequestBody LoanApplicationApprovalRequest request) {
        return ApiResponse.success("Loan application approved", applicationService.approve(
                applicationId, authentication.getName(), request));
    }

    @PostMapping("/admin/applications/{applicationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<LoanApplicationResponse> rejectApplication(
            @PathVariable String applicationId,
            Authentication authentication,
            @Valid @RequestBody LoanApplicationRejectionRequest request) {
        return ApiResponse.success("Loan application rejected", applicationService.reject(
                applicationId, authentication.getName(), request));
    }

    @GetMapping("/{id}")
    ApiResponse<LoanDetailsResponse> details(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Loan details", service.details(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/balance")
    ApiResponse<LoanBalanceResponse> balance(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Loan balance", service.balance(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/schedule")
    ApiResponse<List<EmiScheduleResponse>> schedule(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("EMI schedule", service.schedule(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/history")
    ApiResponse<List<LoanRepaymentResponse>> history(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Loan repayment history", service.history(id, authentication.getName(), isAdmin(authentication)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<LoanDetailsResponse> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateLoanStatusRequest request) {
        return ApiResponse.success("Loan status updated", service.updateStatus(id, request));
    }

    @PostMapping("/calculate")
    ApiResponse<EmiCalculationResponse> calculate(@Valid @RequestBody CalculateEmiRequest request) {
        return ApiResponse.success("EMI calculation", service.calculate(request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
