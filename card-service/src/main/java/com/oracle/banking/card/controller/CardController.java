package com.oracle.banking.card.controller;

import com.oracle.banking.card.dto.CardDtos.CardApplicationApprovalRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationRejectionRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationRequest;
import com.oracle.banking.card.dto.CardDtos.CardApplicationResponse;
import com.oracle.banking.card.dto.CardDtos.CardBlockRequest;
import com.oracle.banking.card.dto.CardDtos.CardLimitUpdateRequest;
import com.oracle.banking.card.dto.CardDtos.CardProductResponse;
import com.oracle.banking.card.dto.CardDtos.CardResponse;
import com.oracle.banking.card.dto.CardDtos.CardStatusResponse;
import com.oracle.banking.card.dto.CardDtos.CreditCardAccountResponse;
import com.oracle.banking.card.entity.CardApplicationStatus;
import com.oracle.banking.card.service.CardApplicationService;
import com.oracle.banking.card.service.CardService;
import com.oracle.banking.card.service.CreditCardAccountService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardService service;
    private final CardApplicationService applicationService;
    private final CreditCardAccountService creditAccounts;

    public CardController(CardService service, CardApplicationService applicationService, CreditCardAccountService creditAccounts) {
        this.service = service;
        this.applicationService = applicationService;
        this.creditAccounts = creditAccounts;
    }

    @GetMapping
    ApiResponse<List<CardResponse>> cards(
            Authentication authentication,
            @RequestParam(required = false) String customerUserId) {
        return ApiResponse.success("Cards", service.cards(
                authentication.getName(), isAdmin(authentication), customerUserId));
    }

    @GetMapping("/products")
    ApiResponse<List<CardProductResponse>> products() {
        return ApiResponse.success("Card products", applicationService.products());
    }

    @PostMapping("/applications")
    ApiResponse<CardApplicationResponse> apply(
            Authentication authentication,
            @Valid @RequestBody CardApplicationRequest request) {
        return ApiResponse.success("Card application submitted", applicationService.apply(authentication.getName(), request));
    }

    @GetMapping("/applications")
    ApiResponse<List<CardApplicationResponse>> myApplications(Authentication authentication) {
        return ApiResponse.success("Card applications", applicationService.myApplications(authentication.getName()));
    }

    @GetMapping("/applications/{applicationId}")
    ApiResponse<CardApplicationResponse> application(@PathVariable String applicationId, Authentication authentication) {
        return ApiResponse.success("Card application", applicationService.application(
                applicationId, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<CardApplicationResponse>> searchApplications(
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) CardApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("Card applications", applicationService.search(customerUserId, status, page, size));
    }

    @PostMapping("/admin/applications/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<CardApplicationResponse> approveApplication(
            @PathVariable String applicationId,
            Authentication authentication,
            @Valid @RequestBody CardApplicationApprovalRequest request) {
        return ApiResponse.success("Card application approved", applicationService.approve(
                applicationId, authentication.getName(), request));
    }

    @PostMapping("/admin/applications/{applicationId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<CardApplicationResponse> rejectApplication(
            @PathVariable String applicationId,
            Authentication authentication,
            @Valid @RequestBody CardApplicationRejectionRequest request) {
        return ApiResponse.success("Card application rejected", applicationService.reject(
                applicationId, authentication.getName(), request));
    }

    @GetMapping("/credit-accounts")
    ApiResponse<List<CreditCardAccountResponse>> creditAccounts(
            Authentication authentication,
            @RequestParam(required = false) String customerUserId) {
        return ApiResponse.success("Credit card accounts", creditAccounts.accounts(
                authentication.getName(), isAdmin(authentication), customerUserId));
    }

    @GetMapping("/{id}")
    ApiResponse<CardResponse> card(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Card", service.card(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/status")
    ApiResponse<CardStatusResponse> status(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Card status", service.status(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/credit-account")
    ApiResponse<CreditCardAccountResponse> creditAccount(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Credit card account", creditAccounts.byCard(
                id, authentication.getName(), isAdmin(authentication)));
    }

    @PostMapping("/{id}/activate")
    ApiResponse<CardResponse> activate(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Card activated", service.activate(id, authentication.getName()));
    }

    @PostMapping("/{id}/block")
    ApiResponse<CardResponse> block(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody(required = false) CardBlockRequest request) {
        CardBlockRequest effectiveRequest = request == null ? new CardBlockRequest(null) : request;
        return ApiResponse.success("Card blocked", service.block(
                id, authentication.getName(), isAdmin(authentication), effectiveRequest));
    }

    @PostMapping("/{id}/unblock")
    ApiResponse<CardResponse> unblock(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Card unblocked", service.unblock(
                id, authentication.getName(), isAdmin(authentication)));
    }

    @PutMapping("/{id}/limit")
    ApiResponse<CardResponse> updateLimit(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody CardLimitUpdateRequest request) {
        return ApiResponse.success("Card limit updated", service.updateLimit(id, authentication.getName(), request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }
}
