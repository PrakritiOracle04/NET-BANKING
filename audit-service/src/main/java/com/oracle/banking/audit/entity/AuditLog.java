package com.oracle.banking.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "AUDIT_LOGS",
        uniqueConstraints = {
            @UniqueConstraint(name = "UK_AUDIT_EVENT_ID", columnNames = "EVENT_ID"),
            @UniqueConstraint(name = "UK_AUDIT_KAFKA_COORD", columnNames = {"TOPIC_NAME", "PARTITION_ID", "RECORD_OFFSET"})
        },
        indexes = {
            @Index(name = "IX_AUDIT_OCCURRED", columnList = "OCCURRED_AT"),
            @Index(name = "IX_AUDIT_ACTOR_OCCURRED", columnList = "ACTOR_USER_ID, OCCURRED_AT"),
            @Index(name = "IX_AUDIT_ACTION_OCCURRED", columnList = "ACTION, OCCURRED_AT"),
            @Index(name = "IX_AUDIT_REFERENCE", columnList = "REFERENCE_ID")
        })
public class AuditLog {
    @Id
    @Column(name = "AUDIT_ID", length = 36)
    private String auditId;
    @Column(name = "EVENT_ID", nullable = false, length = 160)
    private String eventId;
    @Column(name = "PRODUCER_EVENT_ID", length = 160)
    private String producerEventId;
    @Column(name = "EVENT_VERSION", nullable = false)
    private Integer eventVersion;
    @Column(name = "EVENT_TYPE", nullable = false, length = 100)
    private String eventType;
    @Column(name = "OCCURRED_AT", nullable = false)
    private Instant occurredAt;
    @Column(name = "INGESTED_AT", nullable = false)
    private Instant ingestedAt;
    @Column(name = "ACTOR_USER_ID", length = 36)
    private String actorUserId;
    @Column(name = "ACTOR_ROLE", length = 40)
    private String actorRole;
    @Column(name = "SOURCE_SERVICE", nullable = false, length = 80)
    private String sourceService;
    @Column(name = "ACTION", nullable = false, length = 100)
    private String action;
    @Column(name = "ENTITY_TYPE", nullable = false, length = 80)
    private String entityType;
    @Column(name = "REFERENCE_ID", length = 160)
    private String referenceId;
    @Column(name = "CORRELATION_ID", length = 160)
    private String correlationId;
    @Column(name = "STATUS", nullable = false, length = 40)
    private String status;
    @Column(name = "SEVERITY", nullable = false, length = 20)
    private String severity;
    @Lob
    @Column(name = "SANITIZED_METADATA")
    private String sanitizedMetadata;
    @Column(name = "TOPIC_NAME", nullable = false, length = 160)
    private String topic;
    @Column(name = "PARTITION_ID", nullable = false)
    private Integer partition;
    @Column(name = "RECORD_OFFSET", nullable = false)
    private Long offset;

    protected AuditLog() {}

    public AuditLog(
            String eventId, String producerEventId, int eventVersion, String eventType, Instant occurredAt,
            String actorUserId, String actorRole, String sourceService, String action, String entityType,
            String referenceId, String correlationId, String status, String severity, String sanitizedMetadata,
            String topic, int partition, long offset) {
        this.auditId = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.producerEventId = producerEventId;
        this.eventVersion = eventVersion;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.sourceService = sourceService;
        this.action = action;
        this.entityType = entityType;
        this.referenceId = referenceId;
        this.correlationId = correlationId;
        this.status = status;
        this.severity = severity;
        this.sanitizedMetadata = sanitizedMetadata;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }

    @PrePersist
    void beforeCreate() {
        if (auditId == null) auditId = UUID.randomUUID().toString();
        if (ingestedAt == null) ingestedAt = Instant.now();
    }

    public String getAuditId() { return auditId; }
    public String getEventId() { return eventId; }
    public String getProducerEventId() { return producerEventId; }
    public Integer getEventVersion() { return eventVersion; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getIngestedAt() { return ingestedAt; }
    public String getActorUserId() { return actorUserId; }
    public String getActorRole() { return actorRole; }
    public String getSourceService() { return sourceService; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getReferenceId() { return referenceId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
    public String getSeverity() { return severity; }
    public String getSanitizedMetadata() { return sanitizedMetadata; }
    public String getTopic() { return topic; }
    public Integer getPartition() { return partition; }
    public Long getOffset() { return offset; }
}
