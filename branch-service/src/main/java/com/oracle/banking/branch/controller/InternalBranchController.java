package com.oracle.banking.branch.controller;

import com.oracle.banking.branch.dto.BranchDtos.Response;
import com.oracle.banking.branch.service.BranchService;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/branches")
public class InternalBranchController {
    private final BranchService service;
    private final String internalApiKey;

    public InternalBranchController(
            BranchService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/ifsc/{ifsc}")
    Response byIfsc(
            @PathVariable String ifsc,
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key) {
        if (!internalApiKey.equals(key)) {
            throw new InvalidInternalKey();
        }
        return service.byIfsc(ifsc);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class InvalidInternalKey extends RuntimeException {}
}
