package com.oracle.banking.scheduler.service;

import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.ExecutionPage;
import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.SchedulePage;
import com.oracle.banking.scheduler.dto.ScheduleOperationsDtos.ScheduleSummary;
import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ExecutionStatus;
import com.oracle.banking.scheduler.entity.ScheduleExecution;
import com.oracle.banking.scheduler.entity.ScheduleOperationType;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import com.oracle.banking.scheduler.repository.BankingScheduleRepository;
import com.oracle.banking.scheduler.repository.ScheduleExecutionRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ScheduleOperationsService {
    private final BankingScheduleRepository schedules;
    private final ScheduleExecutionRepository executions;

    public ScheduleOperationsService(BankingScheduleRepository schedules, ScheduleExecutionRepository executions) {
        this.schedules = schedules;
        this.executions = executions;
    }

    public SchedulePage search(
            String customerUserId, ScheduleOperationType operationType, ScheduleStatus status,
            Boolean systemOwned, int page, int size) {
        Specification<BankingSchedule> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (operationType != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("operationType"), operationType));
        if (status != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        if (systemOwned != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("systemOwned"), systemOwned));
        return SchedulePage.from(schedules.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    public ExecutionPage searchExecutions(
            String customerUserId, String scheduleId, ExecutionStatus status, int page, int size) {
        Specification<ScheduleExecution> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(
                    root.join("schedule", JoinType.INNER).get("customerUserId"), customerUserId));
        }
        if (scheduleId != null && !scheduleId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("schedule").get("scheduleId"), scheduleId));
        }
        if (status != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        return ExecutionPage.from(executions.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledFor"))));
    }

    public ScheduleSummary summary() {
        return new ScheduleSummary(
                schedules.count(), schedules.countByStatus(ScheduleStatus.ACTIVE),
                schedules.countByStatus(ScheduleStatus.PAUSED), schedules.countByStatus(ScheduleStatus.FAILED),
                schedules.countBySystemOwnedTrue(), executions.count(),
                executions.countByStatus(ExecutionStatus.SUCCEEDED), executions.countByStatus(ExecutionStatus.FAILED));
    }
}
