package com.oracle.banking.report.controller;

import com.oracle.banking.report.dto.ReportDtos.QueuedReport;
import com.oracle.banking.report.dto.ReportDtos.ReportHistory;
import com.oracle.banking.report.dto.ReportDtos.ReportJobResponse;
import com.oracle.banking.report.dto.ReportDtos.ReportRequest;
import com.oracle.banking.report.entity.ReportType;
import com.oracle.banking.report.service.ReportEventPublisher;
import com.oracle.banking.report.service.ReportJobService;
import com.oracle.banking.report.service.ReportJobService.DownloadableReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportJobService service;
    private final ReportEventPublisher events;

    public ReportController(ReportJobService service, ReportEventPublisher events) {
        this.service = service;
        this.events = events;
    }

    @PostMapping("/account-statements")
    ResponseEntity<QueuedReport> accountStatement(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.ACCOUNT_STATEMENT, request, key, authentication);
    }

    @PostMapping("/transactions")
    ResponseEntity<QueuedReport> transactions(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.TRANSACTIONS, request, key, authentication);
    }

    @PostMapping("/customers")
    ResponseEntity<QueuedReport> customers(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.CUSTOMERS, request, key, authentication);
    }

    @PostMapping("/cards")
    ResponseEntity<QueuedReport> cards(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.CARDS, request, key, authentication);
    }

    @PostMapping("/loans")
    ResponseEntity<QueuedReport> loans(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.LOANS, request, key, authentication);
    }

    @PostMapping("/bill-payments")
    ResponseEntity<QueuedReport> billPayments(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.BILL_PAYMENTS, request, key, authentication);
    }

    @PostMapping("/schedules")
    ResponseEntity<QueuedReport> schedules(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.SCHEDULES, request, key, authentication);
    }

    @PostMapping("/admin-overview")
    ResponseEntity<QueuedReport> adminOverview(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.ADMIN_OVERVIEW, request, key, authentication);
    }

    @PostMapping("/audit")
    ResponseEntity<QueuedReport> audit(@Valid @RequestBody ReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        return queued(ReportType.AUDIT, request, key, authentication);
    }

    @GetMapping("/history")
    ReportHistory history(
            @RequestParam(required = false) String requesterUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        return service.history(requesterUserId, authentication.getName(), isAdmin(authentication), page, size);
    }

    @GetMapping("/{id}")
    ReportJobResponse get(@PathVariable String id, Authentication authentication) {
        return service.get(id, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/{id}/download")
    ResponseEntity<byte[]> download(@PathVariable String id, Authentication authentication) throws Exception {
        DownloadableReport downloadable = service.download(id, authentication.getName(), isAdmin(authentication));
        byte[] content = Files.readAllBytes(Path.of(downloadable.report().getStoragePath()));
        events.downloaded(downloadable.job());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(downloadable.report().getContentType()));
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(downloadable.report().getFileName(), java.nio.charset.StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private ResponseEntity<QueuedReport> queued(
            ReportType type, ReportRequest request, String key, Authentication authentication) {
        String role = isAdmin(authentication) ? "ADMIN" : "CUSTOMER";
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.queue(type, request, authentication.getName(), role, key));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
