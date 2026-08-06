package com.oracle.banking.customer.repository;

import com.oracle.banking.customer.entity.KycDocument;
import com.oracle.banking.customer.entity.KycDocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, String> {
    List<KycDocument> findAllByUserIdOrderByUploadedAtDesc(String userId);
    Optional<KycDocument> findByDocumentIdAndUserId(String documentId, String userId);
    Optional<KycDocument> findByUserIdAndDocumentType(String userId, KycDocumentType documentType);
    long countByUserId(String userId);
    boolean existsByUserIdAndDocumentType(String userId, KycDocumentType documentType);
}
