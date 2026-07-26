package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "NOTIFICATION")
public class Notification {

    @Id
    @Column(name = "NOTIFICATION_ID")
    private String notificationId;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Column(name = "NOTIFICATION_TYPE", length = 30)
    private String notificationType;

    @Column(name = "CHANNEL", length = 20)
    private String channel;

    @Column(name = "MESSAGE", length = 500)
    private String message;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "SENT_AT")
    private LocalDateTime sentAt;

    public Notification() {
    }

    public Notification(String notificationId, AppUser user,
                        String notificationType, String channel,
                        String message, String status,
                        LocalDateTime sentAt) {
        this.notificationId = notificationId;
        this.user = user;
        this.notificationType = notificationType;
        this.channel = channel;
        this.message = message;
        this.status = status;
        this.sentAt = sentAt;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "notificationId='" + notificationId + '\'' +
                ", notificationType='" + notificationType + '\'' +
                ", channel='" + channel + '\'' +
                ", status='" + status + '\'' +
                ", sentAt=" + sentAt +
                '}';
    }
}