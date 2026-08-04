package com.oracle.banking.scheduler.repository;

import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ExecutionStatus;
import com.oracle.banking.scheduler.entity.ScheduleExecution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExecutionRepository extends JpaRepository<ScheduleExecution, String> {
    Optional<ScheduleExecution> findByScheduleAndScheduledFor(BankingSchedule schedule, Instant scheduledFor);
    List<ScheduleExecution> findByScheduleOrderByScheduledForDesc(BankingSchedule schedule);
    List<ScheduleExecution> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(ExecutionStatus status, Instant now);
}
