package com.oracle.banking.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "REPORT_JOBS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_REPORT_REQUESTER_KEY", columnNames = {"REQUESTER_USER_ID", "IDEMPOTENCY_KEY"}),
        indexes = {
            @Index(name = "IX_REPORT_JOB_REQUESTER_CREATED", columnList = "REQUESTER_USER_ID, CREATED_AT"),
            @Index(name = "IX_REPORT_JOB_STATUS_UPDATED", columnList = "STATUS, UPDATED_AT")
        })
public class ReportJob {
    @Id
    @Column(name = "REPORT_JOB_ID", length = 36)
    private String reportJobId;
    @Column(name = "REQUESTER_USER_ID", nullable = false, length = 36)
    private String requesterUserId;
    @Column(name = "REQUESTER_ROLE", nullable = false, length = 40)
    private String requesterRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "REPORT_TYPE", nullable = false, length = 40)
    private ReportType reportType;
    @Enumerated(EnumType.STRING)
    @Column(name = "REPORT_FORMAT", nullable = false, length = 10)
    private ReportFormat reportFormat;
    @Lob
    @Column(name = "FILTER_SNAPSHOT", nullable = false)
    private String filterSnapshot;
    @Column(name = "REQUEST_FINGERPRINT", nullable = false, length = 64)
    private String requestFingerprint;
    @Column(name = "IDEMPOTENCY_KEY", length = 160)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ReportJobStatus status;
    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private Integer attemptCount;
    @Column(name = "FAILURE_REASON", length = 500)
    private String failureReason;
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
    @Column(name = "STARTED_AT")
    private Instant startedAt;
    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    protected ReportJob() {}

    public ReportJob(
            String requesterUserId, String requesterRole, ReportType reportType, ReportFormat reportFormat,
            String filterSnapshot, String requestFingerprint, String idempotencyKey) {
        this.reportJobId = UUID.randomUUID().toString();
        this.requesterUserId = requesterUserId;
        this.requesterRole = requesterRole;
        this.reportType = reportType;
        this.reportFormat = reportFormat;
        this.filterSnapshot = filterSnapshot;
        this.requestFingerprint = requestFingerprint;
        this.idempotencyKey = idempotencyKey;
        this.status = ReportJobStatus.QUEUED;
        this.attemptCount = 0;
    }

    @PrePersist
    void beforeCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public void running() { status = ReportJobStatus.RUNNING; attemptCount++; startedAt = Instant.now(); failureReason = null; }
    public void queuedForRecovery() { status = ReportJobStatus.QUEUED; startedAt = null; }
    public void complete() { status = ReportJobStatus.COMPLETED; completedAt = Instant.now(); failureReason = null; }
    public void fail(String reason) { status = ReportJobStatus.FAILED; completedAt = Instant.now(); failureReason = bounded(reason); }
    public void expire() { status = ReportJobStatus.EXPIRED; }
    private String bounded(String value) { return value == null ? null : value.substring(0, Math.min(500, value.length())); }

    public String getReportJobId() { return reportJobId; }
    public String getRequesterUserId() { return requesterUserId; }
    public String getRequesterRole() { return requesterRole; }
    public ReportType getReportType() { return reportType; }
    public ReportFormat getReportFormat() { return reportFormat; }
    public String getFilterSnapshot() { return filterSnapshot; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public ReportJobStatus getStatus() { return status; }
    public Integer getAttemptCount() { return attemptCount; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
