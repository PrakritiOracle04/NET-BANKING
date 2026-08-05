package com.oracle.banking.scheduler.dto;

import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ScheduleExecution;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class ScheduleOperationsDtos {
    private ScheduleOperationsDtos() {}

    public record ScheduleItem(
            String scheduleId, String customerUserId, String operationType, String scheduleType,
            String sourceAccountId, BigDecimal amount, boolean systemOwned, String status,
            Instant nextExecutionAt, Instant createdAt) {
        public static ScheduleItem from(BankingSchedule schedule) {
            return new ScheduleItem(
                    schedule.getScheduleId(), schedule.getCustomerUserId(), schedule.getOperationType().name(),
                    schedule.getScheduleType().name(), schedule.getSourceAccountId(), schedule.getAmount(),
                    schedule.isSystemOwned(), schedule.getStatus().name(), schedule.getNextExecutionAt(),
                    schedule.getCreatedAt());
        }
    }

    public record SchedulePage(List<ScheduleItem> items, int page, int size, long totalElements, int totalPages) {
        public static SchedulePage from(Page<BankingSchedule> result) {
            return new SchedulePage(result.getContent().stream().map(ScheduleItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record ExecutionItem(
            String executionId, String scheduleId, String customerUserId, String operationType,
            Instant scheduledFor, int attemptCount, String status, String workflowReference,
            String failureReason, Instant startedAt, Instant completedAt) {
        public static ExecutionItem from(ScheduleExecution execution) {
            BankingSchedule schedule = execution.getSchedule();
            return new ExecutionItem(
                    execution.getExecutionId(), schedule.getScheduleId(), schedule.getCustomerUserId(),
                    schedule.getOperationType().name(), execution.getScheduledFor(), execution.getAttemptCount(),
                    execution.getStatus().name(), execution.getWorkflowReference(), execution.getFailureReason(),
                    execution.getStartedAt(), execution.getCompletedAt());
        }
    }

    public record ExecutionPage(List<ExecutionItem> items, int page, int size, long totalElements, int totalPages) {
        public static ExecutionPage from(Page<ScheduleExecution> result) {
            return new ExecutionPage(result.getContent().stream().map(ExecutionItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record ScheduleSummary(
            long total, long active, long paused, long failed, long systemOwned,
            long executions, long executionsSucceeded, long executionsFailed) {}
}
