package com.oracle.banking.scheduler.repository;

import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ScheduleOperationType;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankingScheduleRepository extends JpaRepository<BankingSchedule, String>, JpaSpecificationExecutor<BankingSchedule> {
    List<BankingSchedule> findTop50ByStatusAndNextExecutionAtLessThanEqualOrderByNextExecutionAtAsc(
            ScheduleStatus status,
            Instant now);
    List<BankingSchedule> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    List<BankingSchedule> findByCustomerUserIdAndStatusOrderByCreatedAtDesc(String customerUserId, ScheduleStatus status);
    List<BankingSchedule> findByStatusOrderByCreatedAtDesc(ScheduleStatus status);
    List<BankingSchedule> findAllByOrderByCreatedAtDesc();
    Optional<BankingSchedule> findFirstByOperationTypeAndSystemOwnedTrueOrderByCreatedAtAsc(ScheduleOperationType operationType);
    List<BankingSchedule> findByOperationTypeAndSystemOwnedTrueOrderByCreatedAtAsc(ScheduleOperationType operationType);
    Optional<BankingSchedule> findBySystemKey(String systemKey);
    long countByStatus(ScheduleStatus status);
    long countBySystemOwnedTrue();
}
