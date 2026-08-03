package com.oracle.banking.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "EMAIL_DELIVERY_LOG",
        indexes = @Index(
                name = "IX_DELIVERY_NOTIF_ATTEMPT",
                columnList = "NOTIFICATION_ID, ATTEMPT_NUMBER"))
public class EmailDeliveryLog {
    @Id
    @Column(name = "DELIVERY_LOG_ID", length = 36)
    private String id;

    @Column(name = "NOTIFICATION_ID", nullable = false, length = 36)
    private String notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "NOTIFICATION_ID",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_DELIVERY_NOTIFICATION"))
    private EmailNotification notification;

    @Column(name = "ATTEMPT_NUMBER", nullable = false)
    private int attempt;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Lob
    @Column(name = "FAILURE_REASON")
    private String failureReason;

    @Column(name = "SMTP_RESPONSE", length = 500)
    private String smtpResponse;

    @Column(name = "DELIVERY_TIMESTAMP", nullable = false)
    private Instant timestamp;

    protected EmailDeliveryLog() {
    }

    public EmailDeliveryLog(
            String notificationId,
            int attempt,
            String status,
            String failureReason,
            String smtpResponse) {
        this.id = UUID.randomUUID().toString();
        this.notificationId = notificationId;
        this.attempt = attempt;
        this.status = status;
        this.failureReason = failureReason;
        this.smtpResponse = smtpResponse;
        this.timestamp = Instant.now();
    }
}
