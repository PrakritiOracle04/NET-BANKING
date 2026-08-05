package com.oracle.banking.report.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportJobStatus;
import com.oracle.banking.report.repository.ReportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportWorker {
    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);
    private final ReportJobRepository jobs;
    private final ReportLifecycleService lifecycle;
    private final ReportDataClient dataClient;
    private final ReportFileGenerator generator;

    public ReportWorker(
            ReportJobRepository jobs, ReportLifecycleService lifecycle,
            ReportDataClient dataClient, ReportFileGenerator generator) {
        this.jobs = jobs;
        this.lifecycle = lifecycle;
        this.dataClient = dataClient;
        this.generator = generator;
    }

    @Scheduled(fixedDelayString = "${report.worker.poll-delay-ms}")
    public void processQueued() {
        for (ReportJob candidate : jobs.findTop10ByStatusOrderByCreatedAtAsc(ReportJobStatus.QUEUED)) {
            ReportJob job = lifecycle.claim(candidate.getReportJobId());
            if (job == null) continue;
            try {
                ArrayNode rows = dataClient.fetch(job);
                lifecycle.complete(job.getReportJobId(), generator.generate(job, rows));
            } catch (Exception ex) {
                log.warn("Report generation failed for job {}", job.getReportJobId());
                lifecycle.fail(job.getReportJobId(), ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${report.worker.recovery-delay-ms}")
    public void recoverAndExpire() {
        lifecycle.recoverStaleJobs();
        lifecycle.expireReports();
    }
}
