package com.oracle.banking.workflow.dto;

import com.oracle.banking.workflow.entity.WorkflowSaga;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class WorkflowOperationsDtos {
    private WorkflowOperationsDtos() {}

    public record WorkflowItem(
            String workflowId,
            String referenceNumber,
            String workflowType,
            String status,
            String customerUserId,
            String sourceAccountId,
            String destinationAccountId,
            BigDecimal amount,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {
        public static WorkflowItem from(WorkflowSaga saga) {
            return new WorkflowItem(
                    saga.getWorkflowId(), saga.getReferenceNumber(), saga.getWorkflowType().name(),
                    saga.getStatus().name(), saga.getCustomerUserId(), saga.getSourceAccountId(),
                    saga.getDestinationAccountId(), saga.getAmount(), saga.getFailureReason(),
                    saga.getCreatedAt(), saga.getUpdatedAt());
        }
    }

    public record WorkflowPage(List<WorkflowItem> items, int page, int size, long totalElements, int totalPages) {
        public static WorkflowPage from(Page<WorkflowSaga> result) {
            return new WorkflowPage(result.getContent().stream().map(WorkflowItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record WorkflowSummary(
            long total, long started, long completed, long failed,
            long compensating, long compensated, long compensationPending) {}
}
