package com.oracle.banking.account.controller;

import com.oracle.banking.account.dto.AccountDtos.BalanceResponse;
import com.oracle.banking.account.dto.AccountDtos.InternalAccountValidationResponse;
import com.oracle.banking.account.dto.AccountDtos.MoneyMovementRequest;
import com.oracle.banking.account.exception.AccountExceptions.Forbidden;
import com.oracle.banking.account.service.AccountService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final AccountService service;
    private final String internalApiKey;

    public InternalAccountController(AccountService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/{id}/validate")
    InternalAccountValidationResponse validate(@PathVariable String id,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key) {
        check(key);
        return service.validate(id);
    }

    @GetMapping("/number/{accountNumber}/validate")
    InternalAccountValidationResponse validateByAccountNumber(@PathVariable String accountNumber,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key) {
        check(key);
        return service.validateByAccountNumber(accountNumber);
    }

    @PostMapping("/{id}/credit")
    BalanceResponse credit(@PathVariable String id, @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @Valid @RequestBody MoneyMovementRequest request) {
        check(key);
        return service.credit(id, request);
    }

    @PostMapping("/{id}/debit")
    BalanceResponse debit(@PathVariable String id, @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @Valid @RequestBody MoneyMovementRequest request) {
        check(key);
        return service.debit(id, request);
    }

    @PostMapping("/{id}/movements/{reference}/reverse")
    BalanceResponse reverse(@PathVariable String id, @PathVariable String reference,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key) {
        check(key);
        return service.reverseMovement(id, reference);
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new Forbidden("Invalid internal API key");
        }
    }
}
