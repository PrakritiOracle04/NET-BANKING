package com.oracle.banking.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "KYC_DOCUMENTS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_KYC_DOCUMENT_USER_TYPE",
                columnNames = {"USER_ID", "DOCUMENT_TYPE"}),
        indexes = {
            @Index(name = "IX_KYC_DOCUMENT_USER", columnList = "USER_ID"),
            @Index(name = "IX_KYC_DOCUMENT_KYC", columnList = "KYC_ID")
        })
public class KycDocument {
    @Id
    @Column(name = "DOCUMENT_ID", length = 36)
    private String documentId;

    @Column(name = "KYC_ID", nullable = false, length = 36)
    private String kycId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "KYC_ID",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "FK_KYC_DOCUMENT_KYC"))
    private CustomerKyc kyc;

    @Column(name = "USER_ID", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 30)
    private KycDocumentType documentType;

    @Column(name = "ORIGINAL_FILE_NAME", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "STORED_FILE_NAME", nullable = false, unique = true, length = 80)
    private String storedFileName;

    @Column(name = "FILE_PATH", nullable = false, length = 500)
    private String filePath;

    @Column(name = "DOCUMENT_URL", nullable = false, length = 500)
    private String documentUrl;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;

    @Column(name = "FILE_SIZE", nullable = false)
    private long fileSize;

    @Column(name = "UPLOADED_AT", nullable = false)
    private Instant uploadedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected KycDocument() {
    }

    public KycDocument(String kycId, String userId, KycDocumentType documentType) {
        this.documentId = UUID.randomUUID().toString();
        this.kycId = kycId;
        this.userId = userId;
        this.documentType = documentType;
        this.uploadedAt = Instant.now();
        this.updatedAt = uploadedAt;
    }

    public void replaceFile(
            String originalFileName,
            String storedFileName,
            String filePath,
            String documentUrl,
            String contentType,
            long fileSize) {
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.filePath = filePath;
        this.documentUrl = documentUrl;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public String getDocumentId() { return documentId; }
    public String getKycId() { return kycId; }
    public String getUserId() { return userId; }
    public KycDocumentType getDocumentType() { return documentType; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public String getFilePath() { return filePath; }
    public String getDocumentUrl() { return documentUrl; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
