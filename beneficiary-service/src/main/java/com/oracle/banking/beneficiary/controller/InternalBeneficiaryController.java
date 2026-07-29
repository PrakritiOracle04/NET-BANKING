package com.oracle.banking.beneficiary.controller;

import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryVerificationResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.VerifyBeneficiaryRequest;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.Forbidden;
import com.oracle.banking.beneficiary.service.BeneficiaryService;
import com.oracle.banking.shared.constants.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/beneficiaries")
public class InternalBeneficiaryController {
    private final BeneficiaryService service;
    private final String internalApiKey;

    public InternalBeneficiaryController(BeneficiaryService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/verify-transfer")
    BeneficiaryVerificationResponse verifyForTransfer(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String key,
            @Valid @RequestBody VerifyBeneficiaryRequest request) {
        check(key);
        return service.verifyForTransfer(request);
    }

    private void check(String key) {
        if (!internalApiKey.equals(key)) {
            throw new Forbidden("Invalid internal API key");
        }
    }
}
