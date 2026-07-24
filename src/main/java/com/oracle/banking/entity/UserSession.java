package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "USER_SESSION")
public class UserSession {

    @Id
    @Column(name = "SESSION_ID")
    private String sessionId;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    private AppUser user;

    @Column(name = "DEVICE_ID", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "DEVICE_NAME", length = 100)
    private String deviceName;

    @Column(name = "DEVICE_TYPE", length = 30)
    private String deviceType;

    @Column(name = "OS", length = 50)
    private String os;

    @Column(name = "BROWSER", length = 50)
    private String browser;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "LOGIN_AT")
    private LocalDateTime loginAt;

    @Column(name = "LAST_ACTIVE_AT")
    private LocalDateTime lastActiveAt;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

    @Column(name = "STATUS", length = 20)
    private String status;

    public UserSession() {
    }

    public UserSession(String sessionId, AppUser user, String deviceId,
                       String deviceName, String deviceType,
                       String os, String browser, String ipAddress,
                       LocalDateTime loginAt,
                       LocalDateTime lastActiveAt,
                       LocalDateTime expiresAt,
                       String status) {

        this.sessionId = sessionId;
        this.user = user;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.os = os;
        this.browser = browser;
        this.ipAddress = ipAddress;
        this.loginAt = loginAt;
        this.lastActiveAt = lastActiveAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(LocalDateTime loginAt) {
        this.loginAt = loginAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "sessionId='" + sessionId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", os='" + os + '\'' +
                ", browser='" + browser + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}