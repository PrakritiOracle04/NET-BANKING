package com.oracle.banking.workflow.service;

import com.oracle.banking.workflow.dto.WorkflowOperationsDtos.WorkflowPage;
import com.oracle.banking.workflow.dto.WorkflowOperationsDtos.WorkflowSummary;
import com.oracle.banking.workflow.entity.WorkflowSaga;
import com.oracle.banking.workflow.entity.WorkflowStatus;
import com.oracle.banking.workflow.entity.WorkflowType;
import com.oracle.banking.workflow.repository.WorkflowSagaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class WorkflowOperationsService {
    private final WorkflowSagaRepository repository;

    public WorkflowOperationsService(WorkflowSagaRepository repository) {
        this.repository = repository;
    }

    public WorkflowPage search(
            String customerUserId, WorkflowType workflowType, WorkflowStatus status, int page, int size) {
        Specification<WorkflowSaga> specification = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (workflowType != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("workflowType"), workflowType));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), status));
        }
        return WorkflowPage.from(repository.findAll(
                specification, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    public WorkflowSummary summary() {
        return new WorkflowSummary(
                repository.count(),
                repository.countByStatus(WorkflowStatus.STARTED),
                repository.countByStatus(WorkflowStatus.COMPLETED),
                repository.countByStatus(WorkflowStatus.FAILED),
                repository.countByStatus(WorkflowStatus.COMPENSATING),
                repository.countByStatus(WorkflowStatus.COMPENSATED),
                repository.countByStatus(WorkflowStatus.COMPENSATION_PENDING));
    }
}
