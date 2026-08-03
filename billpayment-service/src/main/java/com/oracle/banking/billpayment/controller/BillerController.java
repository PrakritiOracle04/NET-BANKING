package com.oracle.banking.billpayment.controller;

import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillerCatalogRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.BillerCatalogResponse;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.CustomerBillerRequest;
import com.oracle.banking.billpayment.dto.BillPaymentDtos.CustomerBillerResponse;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.service.BillPaymentService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/billers")
public class BillerController {
    private final BillPaymentService service;

    public BillerController(BillPaymentService service) { this.service = service; }

    @GetMapping("/catalog")
    ApiResponse<List<BillerCatalogResponse>> catalog(@RequestParam(required = false) BillerCategory category) {
        return ApiResponse.success("Biller catalog", service.catalog(category));
    }

    @GetMapping("/catalog/{id}")
    ApiResponse<BillerCatalogResponse> catalogBiller(@PathVariable String id) {
        return ApiResponse.success("Biller", service.activeCatalogBiller(id));
    }

    @PostMapping("/catalog")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<BillerCatalogResponse> createCatalog(@Valid @RequestBody BillerCatalogRequest request) {
        return ApiResponse.success("Biller created", service.createCatalogBiller(request));
    }

    @PutMapping("/catalog/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<BillerCatalogResponse> updateCatalog(@PathVariable String id, @Valid @RequestBody BillerCatalogRequest request) {
        return ApiResponse.success("Biller updated", service.updateCatalogBiller(id, request));
    }

    @DeleteMapping("/catalog/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivateCatalog(@PathVariable String id) { service.deactivateCatalogBiller(id); }

    @GetMapping
    ApiResponse<List<CustomerBillerResponse>> customerBillers(Authentication authentication) {
        return ApiResponse.success("Registered billers", service.customerBillers(authentication.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CustomerBillerResponse> register(
            Authentication authentication,
            @Valid @RequestBody CustomerBillerRequest request) {
        return ApiResponse.success("Biller registered", service.registerBiller(authentication.getName(), request));
    }

    @GetMapping("/{id}")
    ApiResponse<CustomerBillerResponse> customerBiller(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Registered biller", service.customerBiller(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    ApiResponse<CustomerBillerResponse> update(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody CustomerBillerRequest request) {
        return ApiResponse.success("Registered biller updated", service.updateCustomerBiller(id, authentication.getName(), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable String id, Authentication authentication) {
        service.deactivateCustomerBiller(id, authentication.getName());
    }
}
