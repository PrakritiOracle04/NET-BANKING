package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "ADMIN_ACTION")
public class AdminAction {

    @Id
    @Column(name = "ADMIN_ACTION_ID")
    private String adminActionId;

    @ManyToOne
    @JoinColumn(name = "ADMIN_USER_ID", nullable = false)
    private AppUser adminUser;

    @ManyToOne
    @JoinColumn(name = "TARGET_USER_ID")
    private AppUser targetUser;

    @Column(name = "ACTION_TYPE", length = 50)
    private String actionType;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "ACTION_AT")
    private LocalDateTime actionAt;

    public AdminAction() {
    }

    public AdminAction(String adminActionId,
                       AppUser adminUser,
                       AppUser targetUser,
                       String actionType,
                       String status,
                       LocalDateTime actionAt) {
        this.adminActionId = adminActionId;
        this.adminUser = adminUser;
        this.targetUser = targetUser;
        this.actionType = actionType;
        this.status = status;
        this.actionAt = actionAt;
    }

    public String getAdminActionId() {
        return adminActionId;
    }

    public void setAdminActionId(String adminActionId) {
        this.adminActionId = adminActionId;
    }

    public AppUser getAdminUser() {
        return adminUser;
    }

    public void setAdminUser(AppUser adminUser) {
        this.adminUser = adminUser;
    }

    public AppUser getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(AppUser targetUser) {
        this.targetUser = targetUser;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getActionAt() {
        return actionAt;
    }

    public void setActionAt(LocalDateTime actionAt) {
        this.actionAt = actionAt;
    }

    @Override
    public String toString() {
        return "AdminAction{" +
                "adminActionId='" + adminActionId + '\'' +
                ", actionType='" + actionType + '\'' +
                ", status='" + status + '\'' +
                ", actionAt=" + actionAt +
                '}';
    }
}