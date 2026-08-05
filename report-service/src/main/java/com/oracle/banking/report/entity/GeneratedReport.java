package com.oracle.banking.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "GENERATED_REPORTS",
        indexes = @Index(name = "IX_GENERATED_REPORT_EXPIRY", columnList = "EXPIRES_AT"))
public class GeneratedReport {
    @Id
    @Column(name = "GENERATED_REPORT_ID", length = 36)
    private String generatedReportId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REPORT_JOB_ID", nullable = false, unique = true,
            foreignKey = @jakarta.persistence.ForeignKey(name = "FK_GENERATED_REPORT_JOB"))
    private ReportJob reportJob;
    @Column(name = "FILE_NAME", nullable = false, length = 180)
    private String fileName;
    @Column(name = "STORAGE_PATH", nullable = false, length = 500)
    private String storagePath;
    @Column(name = "CONTENT_TYPE", nullable = false, length = 80)
    private String contentType;
    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;
    @Column(name = "SHA256_CHECKSUM", nullable = false, length = 64)
    private String checksum;
    @Column(name = "ROW_COUNT", nullable = false)
    private Integer rowCount;
    @Column(name = "GENERATED_AT", nullable = false)
    private Instant generatedAt;
    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    protected GeneratedReport() {}

    public GeneratedReport(
            ReportJob reportJob, String fileName, String storagePath, String contentType,
            long fileSize, String checksum, int rowCount, Instant expiresAt) {
        this.generatedReportId = UUID.randomUUID().toString();
        this.reportJob = reportJob;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.rowCount = rowCount;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void beforeCreate() { if (generatedAt == null) generatedAt = Instant.now(); }

    public String getGeneratedReportId() { return generatedReportId; }
    public ReportJob getReportJob() { return reportJob; }
    public String getFileName() { return fileName; }
    public String getStoragePath() { return storagePath; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public String getChecksum() { return checksum; }
    public Integer getRowCount() { return rowCount; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
