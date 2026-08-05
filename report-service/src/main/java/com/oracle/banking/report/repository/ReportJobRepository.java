package com.oracle.banking.report.repository;

import com.oracle.banking.report.entity.ReportJob;
import com.oracle.banking.report.entity.ReportJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ReportJobRepository extends JpaRepository<ReportJob, String> {
    Optional<ReportJob> findByRequesterUserIdAndIdempotencyKey(String requesterUserId, String idempotencyKey);
    Page<ReportJob> findByRequesterUserIdOrderByCreatedAtDesc(String requesterUserId, Pageable pageable);
    Page<ReportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ReportJob> findTop10ByStatusOrderByCreatedAtAsc(ReportJobStatus status);
    List<ReportJob> findByStatusAndStartedAtBefore(ReportJobStatus status, Instant cutoff);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportJob> findWithLockByReportJobId(String reportJobId);
}
