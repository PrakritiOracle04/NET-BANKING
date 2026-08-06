package com.oracle.banking.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.banking.customer.dto.KycDocumentDtos.DocumentResponse;
import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.KycDocument;
import com.oracle.banking.customer.entity.KycDocumentType;
import com.oracle.banking.customer.event.CustomerAuditPublisher;
import com.oracle.banking.customer.exception.CustomerExceptions.BadRequest;
import com.oracle.banking.customer.exception.CustomerExceptions.NotFound;
import com.oracle.banking.customer.repository.CustomerKycRepository;
import com.oracle.banking.customer.repository.KycDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class KycDocumentServiceTest {
    private static final String USER_ID = "cccbbdb0-d406-4076-94af-d440e055bedf";

    @TempDir
    Path storage;

    private KycDocumentRepository documents;
    private CustomerKycRepository kycRecords;
    private CustomerAuditPublisher auditEvents;
    private KycDocumentService service;

    @BeforeEach
    void setUp() {
        documents = mock(KycDocumentRepository.class);
        kycRecords = mock(CustomerKycRepository.class);
        auditEvents = mock(CustomerAuditPublisher.class);
        service = new KycDocumentService(
                documents,
                kycRecords,
                auditEvents,
                storage.toString(),
                "5MB",
                "http://localhost:8080/");
        service.initializeStorage();
    }

    @Test
    void uploadsPdfAndReturnsProtectedCustomerUrl() throws Exception {
        CustomerKyc kyc = submittedKyc();
        when(kycRecords.findByUserId(USER_ID)).thenReturn(Optional.of(kyc));
        when(documents.findByUserIdAndDocumentType(USER_ID, KycDocumentType.AADHAAR))
                .thenReturn(Optional.empty());
        when(documents.saveAndFlush(any(KycDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.upload(USER_ID, KycDocumentType.AADHAAR, pdf("aadhaar.pdf"));

        assertThat(response.documentUrl()).startsWith("http://localhost:8080/api/customers/me/kyc/documents/");
        assertThat(response.documentUrl()).endsWith("/content");
        assertThat(response.documentType()).isEqualTo(KycDocumentType.AADHAAR);

        ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
        verify(documents).saveAndFlush(captor.capture());
        assertThat(Files.readAllBytes(Path.of(captor.getValue().getFilePath())))
                .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchItsType() {
        when(kycRecords.findByUserId(USER_ID)).thenReturn(Optional.of(submittedKyc()));
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "aadhaar.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(USER_ID, KycDocumentType.AADHAAR, fakePdf))
                .isInstanceOf(BadRequest.class)
                .hasMessageContaining("content does not match");
    }

    @Test
    void blocksChangesAfterKycVerification() {
        CustomerKyc kyc = submittedKyc();
        kyc.verify();
        when(kycRecords.findByUserId(USER_ID)).thenReturn(Optional.of(kyc));

        assertThatThrownBy(() -> service.upload(USER_ID, KycDocumentType.PAN, pdf("pan.pdf")))
                .isInstanceOf(BadRequest.class)
                .hasMessageContaining("after KYC verification");
    }

    @Test
    void neverReturnsAnotherCustomersDocument() {
        when(documents.findByDocumentIdAndUserId("document-id", USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ownContent(USER_ID, "document-id"))
                .isInstanceOf(NotFound.class);
    }

    @Test
    void replacingARejectedDocumentReturnsKycToPending() {
        CustomerKyc kyc = submittedKyc();
        kyc.reject("Unreadable document");
        when(kycRecords.findByUserId(USER_ID)).thenReturn(Optional.of(kyc));
        when(documents.findByUserIdAndDocumentType(USER_ID, KycDocumentType.PAN))
                .thenReturn(Optional.empty());
        when(documents.saveAndFlush(any(KycDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(USER_ID, KycDocumentType.PAN, pdf("pan.pdf"));

        assertThat(kyc.getStatus().name()).isEqualTo("PENDING");
        assertThat(kyc.getRejectionReason()).isNull();
        verify(auditEvents).statusChanged(kyc);
    }

    @Test
    void replacementRemovesThePreviousPhysicalFile() throws Exception {
        CustomerKyc kyc = submittedKyc();
        Path customerDirectory = Files.createDirectories(storage.resolve(USER_ID));
        Path oldPath = customerDirectory.resolve("old.pdf");
        Files.writeString(oldPath, "%PDF-old", StandardCharsets.US_ASCII);
        KycDocument existing = new KycDocument(kyc.getKycId(), USER_ID, KycDocumentType.AADHAAR);
        existing.replaceFile(
                "old.pdf",
                "old.pdf",
                oldPath.toString(),
                "http://localhost:8080/old",
                "application/pdf",
                Files.size(oldPath));

        when(kycRecords.findByUserId(USER_ID)).thenReturn(Optional.of(kyc));
        when(documents.findByUserIdAndDocumentType(USER_ID, KycDocumentType.AADHAAR))
                .thenReturn(Optional.of(existing));
        when(documents.saveAndFlush(any(KycDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(USER_ID, KycDocumentType.AADHAAR, pdf("new.pdf"));

        assertThat(oldPath).doesNotExist();
        assertThat(Path.of(existing.getFilePath())).exists();
    }

    private CustomerKyc submittedKyc() {
        CustomerKyc kyc = new CustomerKyc(USER_ID);
        kyc.submit("encrypted-aadhaar", "1234", "aadhaar-hash", "encrypted-pan", "1234", "pan-hash");
        return kyc;
    }

    private MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile(
                "file",
                fileName,
                "application/pdf",
                "%PDF-1.7\nexample".getBytes(StandardCharsets.US_ASCII));
    }
}
