package com.oracle.banking.report.service;

import com.oracle.banking.report.entity.GeneratedReport;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportJobStatus;
import com.oracle.banking.report.repository.GeneratedReportRepository;
import com.oracle.banking.report.repository.ReportJobRepository;
import com.oracle.banking.report.service.ReportFileGenerator.ReportArtifact;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportLifecycleService {
    private final ReportJobRepository jobs;
    private final GeneratedReportRepository reports;
    private final ReportEventPublisher events;
    private final Duration retention;
    private final Duration jobTimeout;

    public ReportLifecycleService(
            ReportJobRepository jobs, GeneratedReportRepository reports, ReportEventPublisher events,
            @Value("${report.retention-hours}") long retentionHours,
            @Value("${report.worker.job-timeout-minutes}") long jobTimeoutMinutes) {
        this.jobs = jobs;
        this.reports = reports;
        this.events = events;
        this.retention = Duration.ofHours(retentionHours);
        this.jobTimeout = Duration.ofMinutes(jobTimeoutMinutes);
    }

    @Transactional
    public ReportJob claim(String id) {
        ReportJob job = jobs.findWithLockByReportJobId(id).orElse(null);
        if (job == null || job.getStatus() != ReportJobStatus.QUEUED) return null;
        job.running();
        return job;
    }

    @Transactional
    public void complete(String id, ReportArtifact artifact) {
        ReportJob job = jobs.findWithLockByReportJobId(id).orElseThrow();
        if (job.getStatus() == ReportJobStatus.COMPLETED) return;
        if (reports.findByReportJob(job).isEmpty()) {
            reports.save(new GeneratedReport(
                    job, artifact.fileName(), artifact.storagePath(), artifact.contentType(), artifact.fileSize(),
                    artifact.checksum(), artifact.rowCount(), Instant.now().plus(retention)));
        }
        job.complete();
        events.generatedAfterCommit(job);
    }

    @Transactional
    public void fail(String id, Throwable failure) {
        ReportJob job = jobs.findWithLockByReportJobId(id).orElseThrow();
        if (job.getStatus() == ReportJobStatus.COMPLETED) return;
        job.fail(failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
        events.failedAfterCommit(job);
    }

    @Transactional
    public void recoverStaleJobs() {
        jobs.findByStatusAndStartedAtBefore(ReportJobStatus.RUNNING, Instant.now().minus(jobTimeout))
                .forEach(ReportJob::queuedForRecovery);
    }

    @Transactional
    public void expireReports() {
        List<GeneratedReport> expired = reports.findByExpiresAtBefore(Instant.now());
        for (GeneratedReport report : expired) {
            ReportJob job = report.getReportJob();
            if (job.getStatus() == ReportJobStatus.EXPIRED) continue;
            try { Files.deleteIfExists(Path.of(report.getStoragePath())); }
            catch (Exception ignored) { continue; }
            job.expire();
            events.expiredAfterCommit(job);
        }
    }
}
