package com.oracle.banking.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.banking.report.dto.ReportDtos.QueuedReport;
import com.oracle.banking.report.dto.ReportDtos.ReportHistory;
import com.oracle.banking.report.dto.ReportDtos.ReportJobResponse;
import com.oracle.banking.report.dto.ReportDtos.ReportRequest;
import com.oracle.banking.report.entity.GeneratedReport;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportType;
import com.oracle.banking.report.repository.GeneratedReportRepository;
import com.oracle.banking.report.repository.ReportJobRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportJobService {
    private final ReportJobRepository jobs;
    private final GeneratedReportRepository reports;
    private final ObjectMapper mapper;
    private final ReportEventPublisher events;
    private final Duration maxRange;

    public ReportJobService(
            ReportJobRepository jobs, GeneratedReportRepository reports,
            ObjectMapper mapper, ReportEventPublisher events,
            @Value("${report.limits.max-range-days}") long maxRangeDays) {
        this.jobs = jobs;
        this.reports = reports;
        this.mapper = mapper;
        this.events = events;
        this.maxRange = Duration.ofDays(maxRangeDays);
    }

    @Transactional
    public QueuedReport queue(
            ReportType type, ReportRequest request, String requesterUserId, String requesterRole,
            String idempotencyKey) {
        validateRole(type, requesterRole);
        validateRequest(type, request);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ownerUserId", "ADMIN".equals(requesterRole) ? request.ownerUserId() : requesterUserId);
        snapshot.put("accountId", request.accountId());
        snapshot.put("from", request.from());
        snapshot.put("to", request.to());
        snapshot.put("filters", request.filters() == null ? Map.of() : request.filters());
        String json = json(snapshot);
        String fingerprint = sha256(type.name() + "|" + request.format().name() + "|" + json);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            ReportJob existing = jobs.findByRequesterUserIdAndIdempotencyKey(requesterUserId, idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.getRequestFingerprint().equals(fingerprint)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another report request");
                }
                return new QueuedReport(existing.getReportJobId(), existing.getStatus().name(), true);
            }
        }
        ReportJob job = jobs.save(new ReportJob(
                requesterUserId, requesterRole, type, request.format(), json, fingerprint,
                idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey));
        events.requestedAfterCommit(job);
        return new QueuedReport(job.getReportJobId(), job.getStatus().name(), false);
    }

    @Transactional(readOnly = true)
    public ReportJobResponse get(String id, String requesterUserId, boolean admin) {
        ReportJob job = ownedJob(id, requesterUserId, admin);
        return ReportJobResponse.from(job, reports.findByReportJob(job).orElse(null));
    }

    @Transactional(readOnly = true)
    public ReportHistory history(String requesterFilter, String requesterUserId, boolean admin, int page, int size) {
        String owner = admin && requesterFilter != null && !requesterFilter.isBlank() ? requesterFilter : requesterUserId;
        Page<ReportJob> result = admin && (requesterFilter == null || requesterFilter.isBlank())
                ? jobs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                : jobs.findByRequesterUserIdOrderByCreatedAtDesc(owner, PageRequest.of(page, size));
        Map<String, GeneratedReport> generated = new LinkedHashMap<>();
        result.forEach(job -> reports.findByReportJob(job).ifPresent(report -> generated.put(job.getReportJobId(), report)));
        return ReportHistory.from(result, generated);
    }

    @Transactional(readOnly = true)
    public DownloadableReport download(String id, String requesterUserId, boolean admin) {
        ReportJob job = ownedJob(id, requesterUserId, admin);
        GeneratedReport report = reports.findByReportJob(job)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generated report is not available"));
        if (report.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Generated report has expired");
        }
        return new DownloadableReport(job, report);
    }

    private ReportJob ownedJob(String id, String requesterUserId, boolean admin) {
        ReportJob job = jobs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report job not found"));
        if (!admin && !job.getRequesterUserId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Report belongs to another user");
        }
        return job;
    }

    private void validateRole(ReportType type, String role) {
        if ((type == ReportType.CUSTOMERS || type == ReportType.ADMIN_OVERVIEW || type == ReportType.AUDIT)
                && !"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This report requires ADMIN role");
        }
    }

    private void validateRequest(ReportType type, ReportRequest request) {
        if (type == ReportType.ACCOUNT_STATEMENT
                && (request.accountId() == null || request.accountId().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account statement requires accountId");
        }
        if (request.from() != null && request.to() != null
                && (request.from().isAfter(request.to())
                        || Duration.between(request.from(), request.to()).compareTo(maxRange) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or excessive report date range");
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid report filters", ex); }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record DownloadableReport(ReportJob job, GeneratedReport report) {}
}
