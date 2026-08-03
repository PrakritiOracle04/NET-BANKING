package com.oracle.banking.billpayment.controller;

import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillPaymentResponse;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import com.oracle.banking.billpayment.service.BillPaymentService;
import com.oracle.banking.shared.response.ApiResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bill-payments")
public class BillPaymentController {
    private final BillPaymentService service;

    public BillPaymentController(BillPaymentService service) { this.service = service; }

    @GetMapping
    ApiResponse<Page<BillPaymentResponse>> payments(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Bill payments", service.history(
                authentication.getName(), null, null, null, null, null, page, size));
    }

    @GetMapping("/history")
    ApiResponse<Page<BillPaymentResponse>> history(
            Authentication authentication,
            @RequestParam(required = false) BillPaymentStatus status,
            @RequestParam(required = false) String sourceAccountId,
            @RequestParam(required = false) String billerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Bill payment history", service.history(
                authentication.getName(), status, sourceAccountId, billerId, from, to, page, size));
    }

    @GetMapping("/{id}")
    ApiResponse<BillPaymentResponse> payment(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Bill payment", service.payment(id, authentication.getName(), isAdmin(authentication)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }
}
