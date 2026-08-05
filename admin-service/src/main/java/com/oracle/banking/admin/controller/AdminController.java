package com.oracle.banking.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.oracle.banking.admin.dto.AdminDtos.Dashboard;
import com.oracle.banking.admin.dto.AdminDtos.GlobalSearch;
import com.oracle.banking.admin.dto.AdminDtos.Section;
import com.oracle.banking.admin.dto.AdminDtos.SystemHealth;
import com.oracle.banking.admin.service.AdminAuditPublisher;
import com.oracle.banking.admin.service.AdminOperationsClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminOperationsClient operations;
    private final AdminAuditPublisher auditPublisher;

    public AdminController(AdminOperationsClient operations, AdminAuditPublisher auditPublisher) {
        this.operations = operations;
        this.auditPublisher = auditPublisher;
    }

    @GetMapping("/dashboard")
    ResponseEntity<Dashboard> dashboard() {
        Dashboard dashboard = operations.dashboard();
        boolean coreUnavailable = java.util.stream.Stream.of("users", "accounts", "transactions")
                .map(dashboard.sections()::get)
                .allMatch(section -> section == null || section.status() == com.oracle.banking.admin.dto.AdminDtos.SectionStatus.UNAVAILABLE);
        return ResponseEntity.status(coreUnavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK).body(dashboard);
    }

    @GetMapping("/customers")
    JsonNode customers(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("customers", parameters); }
    @GetMapping("/accounts")
    JsonNode accounts(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("accounts", parameters); }
    @GetMapping("/beneficiaries")
    JsonNode beneficiaries(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("beneficiaries", parameters); }
    @GetMapping("/transactions")
    JsonNode transactions(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("transactions", parameters); }
    @GetMapping("/workflows")
    JsonNode workflows(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("workflows", parameters); }
    @GetMapping("/loans")
    JsonNode loans(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("loans", parameters); }
    @GetMapping("/cards")
    JsonNode cards(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("cards", parameters); }
    @GetMapping("/branches")
    JsonNode branches(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("branches", parameters); }
    @GetMapping("/bill-payments")
    JsonNode billPayments(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("bill-payments", parameters); }
    @GetMapping("/schedules")
    JsonNode schedules(@RequestParam MultiValueMap<String, String> parameters) { return operations.search("schedules", parameters); }

    @GetMapping("/audit-summary")
    Section auditSummary() { return operations.auditSummary(); }

    @GetMapping("/system")
    SystemHealth system() { return operations.systemHealth(); }

    @GetMapping("/search")
    GlobalSearch search(@RequestParam String query, Authentication authentication) {
        GlobalSearch result = operations.globalSearch(query);
        auditPublisher.globalSearch(authentication.getName(), result.groups().size());
        return result;
    }
}
