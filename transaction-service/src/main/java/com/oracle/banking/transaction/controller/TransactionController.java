package com.oracle.banking.transaction.controller;

import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.transaction.dto.TransactionDtos.StatementResponse;
import com.oracle.banking.transaction.dto.TransactionDtos.TransactionResponse;
import com.oracle.banking.transaction.entity.TransactionStatus;
import com.oracle.banking.transaction.entity.TransactionType;
import com.oracle.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<Page<TransactionResponse>> transactions(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Transactions", service.list(authentication.getName(), isAdmin(authentication), page, size));
    }

    @GetMapping("/{id}")
    ApiResponse<TransactionResponse> byId(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Transaction", service.byId(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/account/{accountId}")
    ApiResponse<Page<TransactionResponse>> byAccount(@PathVariable String accountId, Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Account transactions", service.byAccount(accountId, authentication.getName(), isAdmin(authentication), page, size));
    }

    @GetMapping("/search")
    ApiResponse<Page<TransactionResponse>> search(Authentication authentication,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success("Transaction search", service.search(authentication.getName(), isAdmin(authentication),
                accountId, accountNumber, transactionType, status, minAmount, maxAmount, referenceNumber,
                fromDate, toDate, page, size, sortBy, direction));
    }

    @GetMapping("/statement")
    ApiResponse<StatementResponse> statement(Authentication authentication,
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        return ApiResponse.success("Statement", service.statement(authentication.getName(), isAdmin(authentication), accountId, fromDate, toDate));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
