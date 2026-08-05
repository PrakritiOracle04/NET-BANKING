package com.oracle.banking.scheduler.dto;

import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ExecutionStatus;
import com.oracle.banking.scheduler.entity.ScheduleExecution;
import com.oracle.banking.scheduler.entity.ScheduleOperationType;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import com.oracle.banking.scheduler.entity.ScheduleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public final class SchedulerDtos {
    private SchedulerDtos() {}

    public record ScheduleRequest(
            @NotNull ScheduleType scheduleType,
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotBlank @Size(max = 36) String customerBillerId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 160) String description,
            @NotBlank @Size(max = 60) String timezone,
            @NotNull Instant startAt,
            Instant endAt,
            @Min(0) @Max(10) Integer maxRetries
    ) {}

    public record ScheduleResponse(
            String scheduleId,
            String customerUserId,
            ScheduleOperationType operationType,
            ScheduleType scheduleType,
            String sourceAccountId,
            String customerBillerId,
            BigDecimal amount,
            String description,
            String timezone,
            Instant startAt,
            Instant nextExecutionAt,
            Instant endAt,
            Integer maxRetries,
            boolean systemOwned,
            ScheduleStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ScheduleResponse from(BankingSchedule schedule) {
            return new ScheduleResponse(
                    schedule.getScheduleId(),
                    schedule.getCustomerUserId(),
                    schedule.getOperationType(),
                    schedule.getScheduleType(),
                    schedule.getSourceAccountId(),
                    schedule.getCustomerBillerId(),
                    schedule.getAmount(),
                    schedule.getDescription(),
                    schedule.getTimezone(),
                    schedule.getStartAt(),
                    schedule.getNextExecutionAt(),
                    schedule.getEndAt(),
                    schedule.getMaxRetries(),
                    schedule.isSystemOwned(),
                    schedule.getStatus(),
                    schedule.getCreatedAt(),
                    schedule.getUpdatedAt());
        }
    }

    public record ExecutionResponse(
            String executionId,
            String scheduleId,
            Instant scheduledFor,
            Integer attemptCount,
            ExecutionStatus status,
            String workflowIdempotencyKey,
            String workflowReference,
            String responseSummary,
            String failureReason,
            Instant startedAt,
            Instant completedAt,
            Instant nextRetryAt
    ) {
        public static ExecutionResponse from(ScheduleExecution execution) {
            return new ExecutionResponse(
                    execution.getExecutionId(),
                    execution.getSchedule().getScheduleId(),
                    execution.getScheduledFor(),
                    execution.getAttemptCount(),
                    execution.getStatus(),
                    execution.getWorkflowIdempotencyKey(),
                    execution.getWorkflowReference(),
                    execution.getResponseSummary(),
                    execution.getFailureReason(),
                    execution.getStartedAt(),
                    execution.getCompletedAt(),
                    execution.getNextRetryAt());
        }
    }

    public record RunDueResponse(int claimed, int succeeded, int retrying, int failed) {}

    public record ScheduledBillPaymentWorkflowRequest(
            String customerUserId,
            String scheduleId,
            Instant scheduledFor,
            String idempotencyKey,
            String sourceAccountId,
            String customerBillerId,
            BigDecimal amount,
            String description
    ) {}

    public record LoanMaintenanceWorkflowRequest(
            ScheduleOperationType operationType,
            Instant scheduledFor,
            LocalDate businessDate,
            String idempotencyKey
    ) {}

    public record WorkflowResponse(boolean success, String message, Map<String, Object> data, Instant timestamp) {}

    public record DomainEvent(
            String eventType,
            String referenceNumber,
            String scheduleId,
            String actorUserId,
            String operationType,
            Instant scheduledFor,
            String status,
            Instant occurredAt,
            String recipient,
            String templateName,
            Map<String, String> variables
    ) {}
}
