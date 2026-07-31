package com.oracle.banking.notification.entity;

import com.oracle.banking.notification.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "EMAIL_NOTIFICATION",
        indexes = {
            @Index(
                    name = "IX_NOTIFICATION_STATUS_CREATED",
                    columnList = "STATUS, CREATED_AT DESC"),
            @Index(
                    name = "IX_NOTIFICATION_CREATED",
                    columnList = "CREATED_AT DESC")
        })
public class EmailNotification {
    @Id
    @Column(name = "NOTIFICATION_ID", length = 36)
    private String id;

    @Column(name = "RECIPIENT", nullable = false, length = 254)
    private String recipient;

    @Column(name = "SUBJECT", nullable = false, length = 250)
    private String subject;

    @Column(name = "BODY_PREVIEW", length = 500)
    private String bodyPreview;

    @Column(name = "NOTIFICATION_TYPE", nullable = false, length = 60)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "SOURCE_EVENT", length = 80)
    private String sourceEvent;

    @Column(name = "REFERENCE_ID", length = 120)
    private String referenceId;

    @Column(name = "TEMPLATE_NAME", length = 80)
    private String templateName;

    @Column(name = "RETRY_COUNT", nullable = false)
    private int retryCount;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "SENT_AT")
    private Instant sentAt;

    protected EmailNotification() {
    }

    public EmailNotification(
            String recipient,
            String subject,
            String bodyPreview,
            String type,
            String templateName,
            String sourceEvent,
            String referenceId) {
        this.id = UUID.randomUUID().toString();
        this.recipient = recipient;
        this.subject = subject;
        this.bodyPreview = bodyPreview;
        this.type = type;
        this.templateName = templateName;
        this.sourceEvent = sourceEvent;
        this.referenceId = referenceId;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyPreview() {
        return bodyPreview;
    }

    public String getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void processing() {
        status = NotificationStatus.PROCESSING;
    }

    public void sent() {
        status = NotificationStatus.SENT;
        sentAt = Instant.now();
    }

    public void failed() {
        status = NotificationStatus.FAILED;
    }

    public void retryScheduled() {
        status = NotificationStatus.RETRYING;
    }

    public void retrying() {
        status = NotificationStatus.RETRYING;
        retryCount++;
    }
}
