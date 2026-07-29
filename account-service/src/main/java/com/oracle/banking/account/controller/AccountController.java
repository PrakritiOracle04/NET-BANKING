package com.oracle.banking.account.controller;

import com.oracle.banking.account.dto.AccountDtos.AccountDetailsResponse;
import com.oracle.banking.account.dto.AccountDtos.AccountSummaryResponse;
import com.oracle.banking.account.dto.AccountDtos.BalanceResponse;
import com.oracle.banking.account.dto.AccountDtos.CreateAccountRequest;
import com.oracle.banking.account.dto.AccountDtos.MiniStatementResponse;
import com.oracle.banking.account.dto.AccountDtos.UpdateAccountStatusRequest;
import com.oracle.banking.account.service.AccountService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<AccountSummaryResponse>> accounts(Authentication authentication,
            @RequestParam(required = false) String customerUsername) {
        return ApiResponse.success("Accounts", service.accountsFor(authentication.getName(), isAdmin(authentication), customerUsername));
    }

    @GetMapping("/{id}")
    ApiResponse<AccountDetailsResponse> details(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Account details", service.details(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/balance")
    ApiResponse<BalanceResponse> balance(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Account balance", service.balance(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/mini-statement")
    ApiResponse<MiniStatementResponse> miniStatement(@PathVariable String id, Authentication authentication,
            @RequestParam(defaultValue = "10") @Min(1) @Max(25) int limit) {
        return ApiResponse.success("Mini statement", service.miniStatement(id, authentication.getName(), isAdmin(authentication), limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<AccountDetailsResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.success("Account created", service.create(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<AccountDetailsResponse> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ApiResponse.success("Account status updated", service.updateStatus(id, request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
