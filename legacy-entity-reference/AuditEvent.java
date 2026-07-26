package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "AUDIT_EVENT")
public class AuditEvent {

    @Id
    @Column(name = "AUDIT_ID")
    private String auditId;

    @ManyToOne
    @JoinColumn(name = "USER_ID")
    private AppUser user;

    @Column(name = "EVENT_TYPE", length = 50)
    private String eventType;

    @Column(name = "ENTITY_NAME", length = 50)
    private String entityName;

    @Column(name = "ENTITY_ID", length = 36)
    private String entityId;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "EVENT_AT")
    private LocalDateTime eventAt;

    public AuditEvent() {
    }

    public AuditEvent(String auditId, AppUser user, String eventType,
                      String entityName, String entityId,
                      String ipAddress, LocalDateTime eventAt) {
        this.auditId = auditId;
        this.user = user;
        this.eventType = eventType;
        this.entityName = entityName;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.eventAt = eventAt;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(LocalDateTime eventAt) {
        this.eventAt = eventAt;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "auditId='" + auditId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", entityName='" + entityName + '\'' +
                ", entityId='" + entityId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", eventAt=" + eventAt +
                '}';
    }
}