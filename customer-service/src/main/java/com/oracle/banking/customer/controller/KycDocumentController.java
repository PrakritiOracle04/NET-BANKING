package com.oracle.banking.customer.controller;

import com.oracle.banking.customer.dto.KycDocumentDtos.DocumentResponse;
import com.oracle.banking.customer.dto.KycDocumentDtos.ReviewSummary;
import com.oracle.banking.customer.entity.KycDocumentType;
import com.oracle.banking.customer.entity.KycStatus;
import com.oracle.banking.customer.service.KycDocumentService;
import com.oracle.banking.customer.service.KycDocumentService.StoredDocument;
import com.oracle.banking.shared.response.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/customers")
public class KycDocumentController {
    private final KycDocumentService service;

    public KycDocumentController(KycDocumentService service) {
        this.service = service;
    }

    @PostMapping(value = "/me/kyc/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<DocumentResponse>> upload(
            Authentication authentication,
            @RequestParam KycDocumentType documentType,
            @RequestPart("file") MultipartFile file) {
        DocumentResponse response = service.upload(authentication.getName(), documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC document uploaded", response));
    }

    @GetMapping("/me/kyc/documents")
    ApiResponse<List<DocumentResponse>> ownDocuments(Authentication authentication) {
        return ApiResponse.success(
                "KYC documents retrieved",
                service.ownDocuments(authentication.getName()));
    }

    @GetMapping("/me/kyc/documents/{documentId}/content")
    ResponseEntity<?> ownContent(Authentication authentication, @PathVariable String documentId) {
        return content(service.ownContent(authentication.getName(), documentId));
    }

    @DeleteMapping("/me/kyc/documents/{documentId}")
    ResponseEntity<Void> deleteOwn(Authentication authentication, @PathVariable String documentId) {
        service.deleteOwn(authentication.getName(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kyc/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<ReviewSummary>> reviews(@RequestParam(required = false) KycStatus status) {
        return ApiResponse.success("KYC review queue retrieved", service.reviews(status));
    }

    @GetMapping("/{userId}/kyc/documents")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<DocumentResponse>> adminDocuments(@PathVariable String userId) {
        return ApiResponse.success("Customer KYC documents retrieved", service.adminDocuments(userId));
    }

    @GetMapping("/{userId}/kyc/documents/{documentId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<?> adminContent(@PathVariable String userId, @PathVariable String documentId) {
        return content(service.adminContent(userId, documentId));
    }

    private ResponseEntity<?> content(StoredDocument document) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.resource());
    }
}
