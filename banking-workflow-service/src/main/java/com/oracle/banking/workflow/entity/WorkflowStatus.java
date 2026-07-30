package com.oracle.banking.workflow.entity;

public enum WorkflowStatus {
    STARTED,
    SOURCE_MOVED,
    DESTINATION_MOVED,
    TRANSACTIONS_RECORDED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    COMPENSATION_PENDING,
    FAILED
}
