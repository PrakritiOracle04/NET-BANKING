package com.oracle.banking.beneficiary.controller;

import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryRequest;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiarySummaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.UpdateBeneficiaryStatusRequest;
import com.oracle.banking.beneficiary.service.BeneficiaryService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {
    private final BeneficiaryService service;

    public BeneficiaryController(BeneficiaryService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<BeneficiarySummaryResponse>> list(Authentication authentication,
            @RequestParam(defaultValue = "false") boolean favouritesOnly) {
        return ApiResponse.success("Beneficiaries", service.list(authentication.getName(), favouritesOnly));
    }

    @GetMapping("/{id}")
    ApiResponse<BeneficiaryResponse> byId(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Beneficiary", service.byId(id, authentication.getName(), isAdmin(authentication)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<BeneficiaryResponse> create(Authentication authentication, @Valid @RequestBody BeneficiaryRequest request) {
        return ApiResponse.success("Beneficiary created", service.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    ApiResponse<BeneficiaryResponse> update(@PathVariable String id, Authentication authentication,
            @Valid @RequestBody BeneficiaryRequest request) {
        return ApiResponse.success("Beneficiary updated", service.update(id, authentication.getName(), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication.getName());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<BeneficiaryResponse> updateStatus(@PathVariable String id, @Valid @RequestBody UpdateBeneficiaryStatusRequest request) {
        return ApiResponse.success("Beneficiary status updated", service.updateStatus(id, request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
