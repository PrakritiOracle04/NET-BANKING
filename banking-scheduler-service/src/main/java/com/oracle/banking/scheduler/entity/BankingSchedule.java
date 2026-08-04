package com.oracle.banking.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "BANKING_SCHEDULES",
        indexes = {
            @Index(name = "IX_SCHEDULE_OWNER_STATUS", columnList = "CUSTOMER_USER_ID, STATUS"),
            @Index(name = "IX_SCHEDULE_DUE", columnList = "STATUS, NEXT_EXECUTION_AT"),
            @Index(name = "IX_SCHEDULE_OPERATION", columnList = "OPERATION_TYPE, SYSTEM_OWNED")
        })
public class BankingSchedule {
    @Id
    @Column(name = "SCHEDULE_ID", length = 36)
    private String scheduleId;

    @Column(name = "CUSTOMER_USER_ID", length = 36)
    private String customerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATION_TYPE", nullable = false, length = 30)
    private ScheduleOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "SCHEDULE_TYPE", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Column(name = "SOURCE_ACCOUNT_ID", length = 36)
    private String sourceAccountId;

    @Column(name = "CUSTOMER_BILLER_ID", length = 36)
    private String customerBillerId;

    @Column(name = "AMOUNT", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "DESCRIPTION", length = 160)
    private String description;

    @Column(name = "TIMEZONE", nullable = false, length = 60)
    private String timezone;

    @Column(name = "START_AT", nullable = false)
    private Instant startAt;

    @Column(name = "NEXT_EXECUTION_AT", nullable = false)
    private Instant nextExecutionAt;

    @Column(name = "END_AT")
    private Instant endAt;

    @Column(name = "REQUESTED_DAY_OF_MONTH")
    private Integer requestedDayOfMonth;

    @Column(name = "MAX_RETRIES", nullable = false)
    private Integer maxRetries;

    @Column(name = "SYSTEM_OWNED", nullable = false)
    private boolean systemOwned;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ScheduleStatus status;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected BankingSchedule() {}

    public BankingSchedule(String customerUserId, ScheduleOperationType operationType, ScheduleType scheduleType,
            String sourceAccountId, String customerBillerId, BigDecimal amount, String description, String timezone,
            Instant startAt, Instant nextExecutionAt, Instant endAt, Integer requestedDayOfMonth, int maxRetries,
            boolean systemOwned) {
        this.scheduleId = UUID.randomUUID().toString();
        this.customerUserId = customerUserId;
        this.operationType = operationType;
        this.scheduleType = scheduleType;
        this.sourceAccountId = sourceAccountId;
        this.customerBillerId = customerBillerId;
        this.amount = amount;
        this.description = description;
        this.timezone = timezone;
        this.startAt = startAt;
        this.nextExecutionAt = nextExecutionAt;
        this.endAt = endAt;
        this.requestedDayOfMonth = requestedDayOfMonth;
        this.maxRetries = maxRetries;
        this.systemOwned = systemOwned;
        this.status = ScheduleStatus.ACTIVE;
    }

    @PrePersist
    void beforeCreate() {
        if (scheduleId == null) scheduleId = UUID.randomUUID().toString();
        if (status == null) status = ScheduleStatus.ACTIVE;
        if (maxRetries == null) maxRetries = 3;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public String getScheduleId() { return scheduleId; }
    public String getCustomerUserId() { return customerUserId; }
    public ScheduleOperationType getOperationType() { return operationType; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public String getSourceAccountId() { return sourceAccountId; }
    public String getCustomerBillerId() { return customerBillerId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getTimezone() { return timezone; }
    public Instant getStartAt() { return startAt; }
    public Instant getNextExecutionAt() { return nextExecutionAt; }
    public Instant getEndAt() { return endAt; }
    public Integer getRequestedDayOfMonth() { return requestedDayOfMonth; }
    public Integer getMaxRetries() { return maxRetries; }
    public boolean isSystemOwned() { return systemOwned; }
    public ScheduleStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateBillPayment(ScheduleType scheduleType, String sourceAccountId, String customerBillerId,
            BigDecimal amount, String description, String timezone, Instant startAt, Instant nextExecutionAt,
            Instant endAt, Integer requestedDayOfMonth, int maxRetries) {
        this.scheduleType = scheduleType;
        this.sourceAccountId = sourceAccountId;
        this.customerBillerId = customerBillerId;
        this.amount = amount;
        this.description = description;
        this.timezone = timezone;
        this.startAt = startAt;
        this.nextExecutionAt = nextExecutionAt;
        this.endAt = endAt;
        this.requestedDayOfMonth = requestedDayOfMonth;
        this.maxRetries = maxRetries;
    }

    public void next(Instant nextExecutionAt) {
        if (nextExecutionAt == null) {
            status = ScheduleStatus.COMPLETED;
        } else {
            this.nextExecutionAt = nextExecutionAt;
            status = ScheduleStatus.ACTIVE;
        }
    }

    public void pause() { status = ScheduleStatus.PAUSED; }
    public void resume(Instant nextExecutionAt) { this.nextExecutionAt = nextExecutionAt; status = ScheduleStatus.ACTIVE; }
    public void cancel() { status = ScheduleStatus.CANCELLED; }
    public void failed() { status = ScheduleStatus.FAILED; }
}
