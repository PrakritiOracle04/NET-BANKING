package com.oracle.banking.scheduler.entity;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED
}
