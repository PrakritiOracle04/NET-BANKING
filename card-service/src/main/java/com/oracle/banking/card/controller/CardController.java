package com.oracle.banking.card.controller;

import com.oracle.banking.card.dto.CardDtos.CardBlockRequest;
import com.oracle.banking.card.dto.CardDtos.CardIssueRequest;
import com.oracle.banking.card.dto.CardDtos.CardLimitUpdateRequest;
import com.oracle.banking.card.dto.CardDtos.CardResponse;
import com.oracle.banking.card.dto.CardDtos.CardStatusResponse;
import com.oracle.banking.card.service.CardService;
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
@RequestMapping("/api/cards")
public class CardController {
    private final CardService service;

    public CardController(CardService service) { this.service = service; }

    @GetMapping
    ApiResponse<List<CardResponse>> cards(
            Authentication authentication,
            @RequestParam(required = false) String customerUserId) {
        return ApiResponse.success("Cards", service.cards(
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

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CardResponse> issue(@Valid @RequestBody CardIssueRequest request) {
        return ApiResponse.success("Card issued", service.issue(request));
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
