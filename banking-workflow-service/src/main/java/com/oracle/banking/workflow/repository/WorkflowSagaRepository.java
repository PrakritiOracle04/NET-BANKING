package com.oracle.banking.workflow.repository;

import com.oracle.banking.workflow.entity.WorkflowSaga;
import com.oracle.banking.workflow.entity.WorkflowStatus;
import com.oracle.banking.workflow.entity.WorkflowType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowSagaRepository extends JpaRepository<WorkflowSaga, String>, JpaSpecificationExecutor<WorkflowSaga> {
    Optional<WorkflowSaga> findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(String customerUserId, String idempotencyKey, WorkflowType workflowType);
    List<WorkflowSaga> findByStatus(WorkflowStatus status);
    long countByStatus(WorkflowStatus status);
}
