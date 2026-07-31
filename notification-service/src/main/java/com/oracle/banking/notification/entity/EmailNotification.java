package com.oracle.banking.notification.entity;

import com.oracle.banking.notification.NotificationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "EMAIL_NOTIFICATION")
public class EmailNotification {
 @Id @Column(name="NOTIFICATION_ID",length=36) private String id;
 @Column(name="RECIPIENT",nullable=false,length=254) private String recipient;
 @Column(name="SUBJECT",nullable=false,length=250) private String subject;
 @Column(name="BODY_PREVIEW",length=500) private String bodyPreview;
 @Column(name="NOTIFICATION_TYPE",nullable=false,length=60) private String type;
 @Enumerated(EnumType.STRING) @Column(name="STATUS",nullable=false,length=20) private NotificationStatus status;
 @Column(name="SOURCE_EVENT",length=80) private String sourceEvent;
 @Column(name="REFERENCE_ID",length=120) private String referenceId;
 @Column(name="TEMPLATE_NAME",length=80) private String templateName;
 @Column(name="RETRY_COUNT",nullable=false) private int retryCount;
 @Column(name="CREATED_AT",nullable=false) private Instant createdAt;
 @Column(name="SENT_AT") private Instant sentAt;
 protected EmailNotification(){}
 public EmailNotification(String recipient,String subject,String preview,String type,String template,String source,String reference){id=UUID.randomUUID().toString();this.recipient=recipient;this.subject=subject;bodyPreview=preview;this.type=type;templateName=template;sourceEvent=source;referenceId=reference;status=NotificationStatus.PENDING;createdAt=Instant.now();}
 public String getId(){return id;} public String getRecipient(){return recipient;} public String getSubject(){return subject;} public String getBodyPreview(){return bodyPreview;} public String getType(){return type;} public NotificationStatus getStatus(){return status;} public String getSourceEvent(){return sourceEvent;} public String getReferenceId(){return referenceId;} public String getTemplateName(){return templateName;} public int getRetryCount(){return retryCount;} public Instant getCreatedAt(){return createdAt;} public Instant getSentAt(){return sentAt;}
 public void processing(){status=NotificationStatus.PROCESSING;} public void sent(){status=NotificationStatus.SENT;sentAt=Instant.now();} public void failed(){status=NotificationStatus.FAILED;} public void retryScheduled(){status=NotificationStatus.RETRYING;} public void retrying(){status=NotificationStatus.RETRYING;retryCount++;}
}
