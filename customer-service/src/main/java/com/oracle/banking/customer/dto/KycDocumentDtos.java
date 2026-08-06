package com.oracle.banking.customer.dto;

import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.KycDocument;
import com.oracle.banking.customer.entity.KycDocumentType;
import com.oracle.banking.customer.entity.KycStatus;
import java.time.Instant;
import java.util.List;

public final class KycDocumentDtos {
    private KycDocumentDtos() {
    }

    public record DocumentResponse(
            String documentId,
            String userId,
            KycDocumentType documentType,
            String originalFileName,
            String contentType,
            long fileSize,
            String documentUrl,
            Instant uploadedAt,
            Instant updatedAt) {

        public static DocumentResponse from(KycDocument document, String responseUrl) {
            return new DocumentResponse(
                    document.getDocumentId(),
                    document.getUserId(),
                    document.getDocumentType(),
                    document.getOriginalFileName(),
                    document.getContentType(),
                    document.getFileSize(),
                    responseUrl,
                    document.getUploadedAt(),
                    document.getUpdatedAt());
        }
    }

    public record ReviewSummary(
            String kycId,
            String userId,
            KycStatus status,
            String rejectionReason,
            long documentCount,
            List<KycDocumentType> uploadedDocumentTypes,
            Instant createdAt,
            Instant updatedAt) {

        public static ReviewSummary from(CustomerKyc kyc, List<KycDocumentType> documentTypes) {
            return new ReviewSummary(
                    kyc.getKycId(),
                    kyc.getUserId(),
                    kyc.getStatus(),
                    kyc.getRejectionReason(),
                    documentTypes.size(),
                    documentTypes,
                    kyc.getCreatedAt(),
                    kyc.getUpdatedAt());
        }
    }
}
