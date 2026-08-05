package com.oracle.banking.audit.repository;

import com.oracle.banking.audit.entity.AuditLog;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {
    Optional<AuditLog> findByEventId(String eventId);
    Optional<AuditLog> findByTopicAndPartitionAndOffset(String topic, Integer partition, Long offset);

    interface ValueCount {
        String getValue();
        long getTotal();
    }

    @Query("select a.eventType as value, count(a) as total from AuditLog a where a.occurredAt between :from and :to group by a.eventType")
    List<ValueCount> countByEventType(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select a.status as value, count(a) as total from AuditLog a where a.occurredAt between :from and :to group by a.status")
    List<ValueCount> countByOutcome(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select a.severity as value, count(a) as total from AuditLog a where a.occurredAt between :from and :to group by a.severity")
    List<ValueCount> countBySeverity(@Param("from") Instant from, @Param("to") Instant to);
}
