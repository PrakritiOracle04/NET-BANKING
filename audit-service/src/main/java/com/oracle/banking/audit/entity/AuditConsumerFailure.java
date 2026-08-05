package com.oracle.banking.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "AUDIT_CONSUMER_FAILURES",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_AUDIT_FAILURE_COORD", columnNames = {"TOPIC_NAME", "PARTITION_ID", "RECORD_OFFSET"}),
        indexes = @Index(name = "IX_AUDIT_FAILURE_CREATED", columnList = "CREATED_AT"))
public class AuditConsumerFailure {
    @Id
    @Column(name = "FAILURE_ID", length = 36)
    private String failureId;
    @Column(name = "TOPIC_NAME", nullable = false, length = 160)
    private String topic;
    @Column(name = "PARTITION_ID", nullable = false)
    private Integer partition;
    @Column(name = "RECORD_OFFSET", nullable = false)
    private Long offset;
    @Column(name = "EVENT_KEY", length = 160)
    private String eventKey;
    @Column(name = "FAILURE_TYPE", nullable = false, length = 120)
    private String failureType;
    @Column(name = "FAILURE_MESSAGE", length = 500)
    private String failureMessage;
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected AuditConsumerFailure() {}

    public AuditConsumerFailure(String topic, int partition, long offset, String eventKey, Throwable failure) {
        this.failureId = UUID.randomUUID().toString();
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.eventKey = bounded(eventKey, 160);
        this.failureType = failure.getClass().getSimpleName();
        this.failureMessage = bounded(failure.getMessage(), 500);
    }

    @PrePersist
    void beforeCreate() { if (createdAt == null) createdAt = Instant.now(); }

    private static String bounded(String value, int length) {
        return value == null ? null : value.substring(0, Math.min(length, value.length()));
    }
}
