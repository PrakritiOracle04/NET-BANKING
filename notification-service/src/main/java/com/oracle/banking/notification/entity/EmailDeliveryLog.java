package com.oracle.banking.notification.entity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="EMAIL_DELIVERY_LOG") public class EmailDeliveryLog {
 @Id @Column(name="DELIVERY_LOG_ID",length=36) private String id; @Column(name="NOTIFICATION_ID",nullable=false,length=36) private String notificationId; @Column(name="ATTEMPT_NUMBER",nullable=false) private int attempt; @Column(name="STATUS",nullable=false,length=20) private String status; @Lob @Column(name="FAILURE_REASON") private String failureReason; @Column(name="SMTP_RESPONSE",length=500) private String smtpResponse; @Column(name="DELIVERY_TIMESTAMP",nullable=false) private Instant timestamp;
 protected EmailDeliveryLog(){} public EmailDeliveryLog(String notificationId,int attempt,String status,String failure,String response){id=UUID.randomUUID().toString();this.notificationId=notificationId;this.attempt=attempt;this.status=status;failureReason=failure;smtpResponse=response;timestamp=Instant.now();}
 public String getId(){return id;} public int getAttempt(){return attempt;} public String getStatus(){return status;} public String getFailureReason(){return failureReason;} public String getSmtpResponse(){return smtpResponse;} public Instant getTimestamp(){return timestamp;}
}
