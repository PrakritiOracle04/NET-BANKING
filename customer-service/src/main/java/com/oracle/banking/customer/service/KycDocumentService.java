package com.oracle.banking.customer.service;

import com.oracle.banking.customer.dto.KycDocumentDtos.DocumentResponse;
import com.oracle.banking.customer.dto.KycDocumentDtos.ReviewSummary;
import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.KycDocument;
import com.oracle.banking.customer.entity.KycDocumentType;
import com.oracle.banking.customer.entity.KycStatus;
import com.oracle.banking.customer.event.CustomerAuditPublisher;
import com.oracle.banking.customer.exception.CustomerExceptions.BadRequest;
import com.oracle.banking.customer.exception.CustomerExceptions.NotFound;
import com.oracle.banking.customer.exception.CustomerExceptions.StorageFailure;
import com.oracle.banking.customer.repository.CustomerKycRepository;
import com.oracle.banking.customer.repository.KycDocumentRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KycDocumentService {
    private static final Map<String, List<String>> ALLOWED_EXTENSIONS = Map.of(
            "application/pdf", List.of(".pdf"),
            "image/jpeg", List.of(".jpg", ".jpeg"),
            "image/png", List.of(".png"));

    private final KycDocumentRepository documents;
    private final CustomerKycRepository kycRecords;
    private final CustomerAuditPublisher auditEvents;
    private final Path storageRoot;
    private final long maxFileSize;
    private final String publicBaseUrl;

    public KycDocumentService(
            KycDocumentRepository documents,
            CustomerKycRepository kycRecords,
            CustomerAuditPublisher auditEvents,
            @Value("${kyc.documents.storage-path}") String storagePath,
            @Value("${kyc.documents.max-file-size}") String maxFileSize,
            @Value("${kyc.documents.public-base-url}") String publicBaseUrl) {
        this.documents = documents;
        this.kycRecords = kycRecords;
        this.auditEvents = auditEvents;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();
        this.publicBaseUrl = removeTrailingSlash(publicBaseUrl);
    }

    @PostConstruct
    void initializeStorage() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new StorageFailure("KYC document storage could not be initialized", exception);
        }
    }

    @Transactional
    public DocumentResponse upload(String userId, KycDocumentType documentType, MultipartFile file) {
        CustomerKyc kyc = requiredKyc(userId);
        if (kyc.getStatus() == KycStatus.VERIFIED) {
            throw new BadRequest("Documents cannot be changed after KYC verification");
        }

        FileDetails fileDetails = validate(file);
        Path customerDirectory = resolveInsideStorage(userId);
        String storedFileName = UUID.randomUUID() + fileDetails.extension();
        Path newFile = customerDirectory.resolve(storedFileName).normalize();
        ensureInsideStorage(newFile);

        try {
            Files.createDirectories(customerDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, newFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageFailure("KYC document could not be stored", exception);
        }

        KycDocument document = documents.findByUserIdAndDocumentType(userId, documentType)
                .orElseGet(() -> new KycDocument(kyc.getKycId(), userId, documentType));
        Path oldFile = document.getFilePath() == null ? null : Path.of(document.getFilePath()).toAbsolutePath().normalize();
        String documentUrl = customerContentUrl(document.getDocumentId());
        document.replaceFile(
                safeOriginalFileName(file.getOriginalFilename()),
                storedFileName,
                newFile.toString(),
                documentUrl,
                fileDetails.contentType(),
                file.getSize());

        try {
            KycDocument saved = documents.saveAndFlush(document);
            if (kyc.getStatus() == KycStatus.REJECTED) {
                kyc.markDocumentsResubmitted();
                auditEvents.statusChanged(kyc);
            }
            if (oldFile != null && !oldFile.equals(newFile)) {
                deleteStoredFile(oldFile);
            }
            return DocumentResponse.from(saved, saved.getDocumentUrl());
        } catch (RuntimeException exception) {
            deleteStoredFile(newFile);
            throw exception;
        }
    }

    public List<DocumentResponse> ownDocuments(String userId) {
        requiredKyc(userId);
        return documents.findAllByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(document -> DocumentResponse.from(document, document.getDocumentUrl()))
                .toList();
    }

    public List<DocumentResponse> adminDocuments(String userId) {
        requiredKyc(userId);
        return documents.findAllByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(document -> DocumentResponse.from(document, adminContentUrl(userId, document.getDocumentId())))
                .toList();
    }

    public StoredDocument ownContent(String userId, String documentId) {
        KycDocument document = documents.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NotFound("KYC document not found"));
        return load(document);
    }

    public StoredDocument adminContent(String userId, String documentId) {
        KycDocument document = documents.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NotFound("KYC document not found for this customer"));
        return load(document);
    }

    @Transactional
    public void deleteOwn(String userId, String documentId) {
        CustomerKyc kyc = requiredKyc(userId);
        if (kyc.getStatus() == KycStatus.VERIFIED) {
            throw new BadRequest("Documents cannot be deleted after KYC verification");
        }
        KycDocument document = documents.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NotFound("KYC document not found"));
        deleteStoredFile(Path.of(document.getFilePath()).toAbsolutePath().normalize());
        documents.delete(document);
    }

    public List<ReviewSummary> reviews(KycStatus status) {
        List<CustomerKyc> reviews = status == null
                ? kycRecords.findAllByOrderByUpdatedAtDesc()
                : kycRecords.findAllByStatusOrderByUpdatedAtDesc(status);
        return reviews.stream().map(kyc -> {
            List<KycDocumentType> types = documents.findAllByUserIdOrderByUploadedAtDesc(kyc.getUserId()).stream()
                    .map(KycDocument::getDocumentType)
                    .sorted(Comparator.comparing(Enum::name))
                    .toList();
            return ReviewSummary.from(kyc, types);
        }).toList();
    }

    private StoredDocument load(KycDocument document) {
        Path path = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        ensureInsideStorage(path);
        if (!Files.isRegularFile(path)) {
            throw new NotFound("Stored KYC document file not found");
        }
        try {
            return new StoredDocument(new UrlResource(path.toUri()), document.getContentType(), document.getOriginalFileName());
        } catch (IOException exception) {
            throw new StorageFailure("KYC document could not be read", exception);
        }
    }

    private FileDetails validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequest("A non-empty document file is required");
        }
        if (file.getSize() > maxFileSize) {
            throw new BadRequest("Document exceeds the maximum allowed size");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        List<String> validExtensions = ALLOWED_EXTENSIONS.get(contentType);
        if (validExtensions == null) {
            throw new BadRequest("Only PDF, JPG, JPEG and PNG documents are allowed");
        }

        String originalName = safeOriginalFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String extension = validExtensions.stream()
                .filter(originalName::endsWith)
                .findFirst()
                .orElseThrow(() -> new BadRequest("File extension does not match the supplied content type"));
        verifySignature(file, contentType);
        return new FileDetails(contentType, extension);
    }

    private void verifySignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid = switch (contentType) {
                case "application/pdf" -> startsWith(header, new int[] {0x25, 0x50, 0x44, 0x46, 0x2D});
                case "image/jpeg" -> startsWith(header, new int[] {0xFF, 0xD8, 0xFF});
                case "image/png" -> startsWith(header, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
                default -> false;
            };
            if (!valid) {
                throw new BadRequest("File content does not match the supplied content type");
            }
        } catch (IOException exception) {
            throw new BadRequest("Document file could not be read");
        }
    }

    private boolean startsWith(byte[] actual, int[] expected) {
        if (actual.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(actual[index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private String safeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "document";
        }
        String normalized = originalFileName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private Path resolveInsideStorage(String userId) {
        Path path = storageRoot.resolve(userId).normalize();
        ensureInsideStorage(path);
        return path;
    }

    private void ensureInsideStorage(Path path) {
        if (!path.startsWith(storageRoot)) {
            throw new BadRequest("Invalid KYC document path");
        }
    }

    private void deleteStoredFile(Path path) {
        ensureInsideStorage(path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new StorageFailure("KYC document file could not be deleted", exception);
        }
    }

    private CustomerKyc requiredKyc(String userId) {
        return kycRecords.findByUserId(userId)
                .orElseThrow(() -> new NotFound("Submit KYC details before uploading documents"));
    }

    private String customerContentUrl(String documentId) {
        return publicBaseUrl + "/api/customers/me/kyc/documents/" + documentId + "/content";
    }

    private String adminContentUrl(String userId, String documentId) {
        return publicBaseUrl + "/api/customers/" + userId + "/kyc/documents/" + documentId + "/content";
    }

    private static String removeTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record FileDetails(String contentType, String extension) {
    }

    public record StoredDocument(Resource resource, String contentType, String originalFileName) {
    }
}
