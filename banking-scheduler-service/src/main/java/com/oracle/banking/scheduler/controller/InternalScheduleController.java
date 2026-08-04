package com.oracle.banking.scheduler.controller;

import com.oracle.banking.scheduler.dto.SchedulerDtos.RunDueResponse;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.Forbidden;
import com.oracle.banking.scheduler.service.BankingSchedulerService;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/schedules")
public class InternalScheduleController {
    private final BankingSchedulerService service;
    private final String internalApiKey;

    public InternalScheduleController(BankingSchedulerService service, @Value("${services.internal-api-key}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/run-due")
    RunDueResponse runDue(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!internalApiKey.equals(suppliedKey)) throw new Forbidden("Invalid internal API key");
        return service.runDue();
    }
}
