package com.oracle.banking.report.repository;

import com.oracle.banking.report.entity.GeneratedReport;
import com.oracle.banking.report.entity.ReportJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, String> {
    Optional<GeneratedReport> findByReportJob(ReportJob reportJob);
    List<GeneratedReport> findByExpiresAtBefore(Instant cutoff);
}
