package com.oracle.banking.audit.controller;

import com.oracle.banking.audit.dto.AuditDtos.AuditItem;
import com.oracle.banking.audit.dto.AuditDtos.AuditPage;
import com.oracle.banking.audit.dto.AuditDtos.AuditSummary;
import com.oracle.banking.audit.service.AuditQueryService;
import com.oracle.banking.audit.service.AuditQueryService.SearchFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    AuditPage search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "occurredAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(new SearchFilter(from, to, actorUserId, action, sourceService, entityType,
                referenceId, correlationId, status, severity), page, size, sort, direction);
    }

    @GetMapping("/{id}")
    AuditItem detail(@PathVariable String id) {
        return service.detail(id);
    }

    @GetMapping("/users/{userId}")
    AuditPage userTimeline(
            @PathVariable String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.search(new SearchFilter(from, to, userId, null, null, null, null, null, null, null),
                page, size, "occurredAt", "desc");
    }

    @GetMapping("/timeline")
    AuditPage timeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.search(new SearchFilter(from, to, actorUserId, action, sourceService, entityType,
                referenceId, correlationId, status, severity), page, size, "occurredAt", "asc");
    }

    @GetMapping("/summary")
    AuditSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.summary(from, to);
    }
}
