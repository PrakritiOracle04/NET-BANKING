package com.oracle.banking.workflow.controller;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.workflow.dto.WorkflowOperationsDtos.WorkflowPage;
import com.oracle.banking.workflow.dto.WorkflowOperationsDtos.WorkflowSummary;
import com.oracle.banking.workflow.entity.WorkflowStatus;
import com.oracle.banking.workflow.entity.WorkflowType;
import com.oracle.banking.workflow.service.WorkflowOperationsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/operations/workflows")
public class WorkflowOperationsController {
    private final WorkflowOperationsService service;
    private final String internalApiKey;

    public WorkflowOperationsController(
            WorkflowOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<WorkflowPage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) WorkflowType workflowType,
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(customerUserId, workflowType, status, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<WorkflowSummary> summary(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
