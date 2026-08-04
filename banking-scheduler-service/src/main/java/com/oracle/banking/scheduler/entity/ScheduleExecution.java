package com.oracle.banking.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "SCHEDULE_EXECUTIONS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_EXEC_WORKFLOW_KEY",
                columnNames = "WORKFLOW_IDEMPOTENCY_KEY"),
        indexes = {
            @Index(name = "IX_EXEC_STATUS_RETRY", columnList = "STATUS, NEXT_RETRY_AT"),
            @Index(name = "IX_EXEC_SCHEDULE", columnList = "SCHEDULE_ID, SCHEDULED_FOR")
        })
public class ScheduleExecution {
    @Id
    @Column(name = "EXECUTION_ID", length = 36)
    private String executionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SCHEDULE_ID", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "FK_EXEC_SCHEDULE"))
    private BankingSchedule schedule;

    @Column(name = "SCHEDULED_FOR", nullable = false)
    private Instant scheduledFor;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private Integer attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "WORKFLOW_IDEMPOTENCY_KEY", nullable = false, unique = true, length = 160)
    private String workflowIdempotencyKey;

    @Column(name = "WORKFLOW_REFERENCE", length = 120)
    private String workflowReference;

    @Column(name = "RESPONSE_SUMMARY", length = 500)
    private String responseSummary;

    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;

    @Column(name = "STARTED_AT")
    private Instant startedAt;

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Column(name = "NEXT_RETRY_AT")
    private Instant nextRetryAt;

    protected ScheduleExecution() {}

    public ScheduleExecution(BankingSchedule schedule, Instant scheduledFor, String workflowIdempotencyKey) {
        this.executionId = UUID.randomUUID().toString();
        this.schedule = schedule;
        this.scheduledFor = scheduledFor;
        this.workflowIdempotencyKey = workflowIdempotencyKey;
        this.attemptCount = 0;
        this.status = ExecutionStatus.PENDING;
    }

    @PrePersist
    void beforeCreate() {
        if (executionId == null) executionId = UUID.randomUUID().toString();
        if (attemptCount == null) attemptCount = 0;
        if (status == null) status = ExecutionStatus.PENDING;
    }

    public String getExecutionId() { return executionId; }
    public BankingSchedule getSchedule() { return schedule; }
    public Instant getScheduledFor() { return scheduledFor; }
    public Integer getAttemptCount() { return attemptCount; }
    public ExecutionStatus getStatus() { return status; }
    public String getWorkflowIdempotencyKey() { return workflowIdempotencyKey; }
    public String getWorkflowReference() { return workflowReference; }
    public String getResponseSummary() { return responseSummary; }
    public String getFailureReason() { return failureReason; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }

    public void running() {
        status = ExecutionStatus.RUNNING;
        attemptCount = attemptCount + 1;
        startedAt = Instant.now();
        failureReason = null;
        nextRetryAt = null;
    }

    public void succeeded(String workflowReference, String responseSummary) {
        status = ExecutionStatus.SUCCEEDED;
        this.workflowReference = workflowReference;
        this.responseSummary = trim(responseSummary);
        completedAt = Instant.now();
        failureReason = null;
    }

    public void retryAt(String reason, Instant nextRetryAt) {
        status = ExecutionStatus.RETRY_WAIT;
        failureReason = trim(reason);
        this.nextRetryAt = nextRetryAt;
    }

    public void failed(String reason) {
        status = ExecutionStatus.FAILED;
        failureReason = trim(reason);
        completedAt = Instant.now();
        nextRetryAt = null;
    }

    private String trim(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 500));
    }
}
