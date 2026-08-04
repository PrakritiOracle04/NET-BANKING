package com.oracle.banking.scheduler.controller;

import com.oracle.banking.scheduler.dto.SchedulerDtos.ExecutionResponse;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ScheduleRequest;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ScheduleResponse;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import com.oracle.banking.scheduler.service.BankingSchedulerService;
import com.oracle.banking.shared.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final BankingSchedulerService service;

    public ScheduleController(BankingSchedulerService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ScheduleResponse> create(Authentication authentication, @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.success("Schedule created", service.create(authentication.getName(), request));
    }

    @GetMapping
    ApiResponse<List<ScheduleResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(required = false) ScheduleStatus status) {
        return ApiResponse.success("Schedules", service.list(authentication.getName(), isAdmin(authentication), customerUserId, status));
    }

    @GetMapping("/{id}")
    ApiResponse<ScheduleResponse> details(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Schedule details", service.details(id, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/{id}/executions")
    ApiResponse<List<ExecutionResponse>> executions(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Schedule executions", service.executions(id, authentication.getName(), isAdmin(authentication)));
    }

    @PutMapping("/{id}")
    ApiResponse<ScheduleResponse> update(@PathVariable String id, Authentication authentication, @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.success("Schedule updated", service.update(id, authentication.getName(), request));
    }

    @PostMapping("/{id}/pause")
    ApiResponse<ScheduleResponse> pause(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Schedule paused", service.pause(id, authentication.getName()));
    }

    @PostMapping("/{id}/resume")
    ApiResponse<ScheduleResponse> resume(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success("Schedule resumed", service.resume(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String id, Authentication authentication) {
        service.cancel(id, authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
