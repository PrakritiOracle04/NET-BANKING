package com.oracle.banking.transaction.controller;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.transaction.dto.TransactionDtos.RecordTransactionRequest;
import com.oracle.banking.transaction.dto.TransactionDtos.TransactionResponse;
import com.oracle.banking.transaction.dto.TransactionDtos.TransactionSummaryResponse;
import com.oracle.banking.transaction.exception.TransactionExceptions.Forbidden;
import com.oracle.banking.transaction.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/transactions")
public class InternalTransactionController {
    private final TransactionService service;
    private final String internalApiKey;

    public InternalTransactionController(TransactionService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse record(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @Valid @RequestBody RecordTransactionRequest request) {
        check(key);
        return service.record(request);
    }

    @GetMapping("/accounts/{accountId}/recent")
    List<TransactionSummaryResponse> recent(@PathVariable String accountId,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @RequestParam(defaultValue = "10") @Min(1) @Max(25) int limit) {
        check(key);
        return service.recentForAccount(accountId, limit);
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new Forbidden("Invalid internal API key");
        }
    }
}
