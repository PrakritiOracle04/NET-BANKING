package com.oracle.banking.report.dto;

import com.oracle.banking.report.entity.GeneratedReport;
import com.oracle.banking.report.entity.ReportFormat;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class ReportDtos {
    private ReportDtos() {}

    public record ReportRequest(
            @NotNull ReportFormat format,
            String ownerUserId,
            String accountId,
            Instant from,
            Instant to,
            Map<String, String> filters) {}

    public record QueuedReport(String reportJobId, String status, boolean idempotentReplay) {}

    public record GeneratedFileMetadata(
            String fileName, String contentType, long fileSize, String checksum,
            int rowCount, Instant generatedAt, Instant expiresAt) {
        public static GeneratedFileMetadata from(GeneratedReport report) {
            return new GeneratedFileMetadata(
                    report.getFileName(), report.getContentType(), report.getFileSize(), report.getChecksum(),
                    report.getRowCount(), report.getGeneratedAt(), report.getExpiresAt());
        }
    }

    public record ReportJobResponse(
            String reportJobId, String requesterUserId, String requesterRole, ReportType reportType,
            ReportFormat format, String status, String failureReason, Instant createdAt,
            Instant startedAt, Instant completedAt, GeneratedFileMetadata generatedFile) {
        public static ReportJobResponse from(ReportJob job, GeneratedReport report) {
            return new ReportJobResponse(
                    job.getReportJobId(), job.getRequesterUserId(), job.getRequesterRole(), job.getReportType(),
                    job.getReportFormat(), job.getStatus().name(), job.getFailureReason(), job.getCreatedAt(),
                    job.getStartedAt(), job.getCompletedAt(), report == null ? null : GeneratedFileMetadata.from(report));
        }
    }

    public record ReportHistory(List<ReportJobResponse> items, int page, int size, long totalElements, int totalPages) {
        public static ReportHistory from(Page<ReportJob> jobs, Map<String, GeneratedReport> reports) {
            return new ReportHistory(jobs.getContent().stream()
                    .map(job -> ReportJobResponse.from(job, reports.get(job.getReportJobId()))).toList(),
                    jobs.getNumber(), jobs.getSize(), jobs.getTotalElements(), jobs.getTotalPages());
        }
    }
}
