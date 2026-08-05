package com.oracle.banking.scheduler.controller;

import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.ExecutionPage;
import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.SchedulePage;
import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.ScheduleSummary;
import com.oracle.banking.scheduler.entity.ExecutionStatus;
import com.oracle.banking.scheduler.entity.ScheduleOperationType;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import com.oracle.banking.scheduler.service.ScheduleOperationsService;
import com.oracle.banking.shared.constants.SecurityConstants;
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
@RequestMapping("/internal/operations/schedules")
public class ScheduleOperationsController {
    private final ScheduleOperationsService service;
    private final String internalApiKey;

    public ScheduleOperationsController(
            ScheduleOperationsService service,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @GetMapping("/search")
    ResponseEntity<SchedulePage> search(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) ScheduleOperationType operationType,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) Boolean systemOwned,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.search(customerUserId, operationType, status, systemOwned, page, size));
    }

    @GetMapping("/executions/search")
    ResponseEntity<ExecutionPage> executions(
            @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) String scheduleId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.searchExecutions(customerUserId, scheduleId, status, page, size));
    }

    @GetMapping("/summary")
    ResponseEntity<ScheduleSummary> summary(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.summary());
    }
}
