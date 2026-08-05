package com.oracle.banking.audit.dto;

import com.oracle.banking.audit.entity.AuditLog;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditItem(
            String auditId, String eventId, int eventVersion, String eventType, Instant occurredAt, Instant ingestedAt,
            String actorUserId, String actorRole, String sourceService, String action, String entityType,
            String referenceId, String correlationId, String status, String severity, String sanitizedMetadata) {
        public static AuditItem from(AuditLog log) {
            return new AuditItem(
                    log.getAuditId(), log.getEventId(), log.getEventVersion(), log.getEventType(), log.getOccurredAt(),
                    log.getIngestedAt(), log.getActorUserId(), log.getActorRole(), log.getSourceService(),
                    log.getAction(), log.getEntityType(), log.getReferenceId(), log.getCorrelationId(),
                    log.getStatus(), log.getSeverity(), log.getSanitizedMetadata());
        }
    }

    public record AuditPage(List<AuditItem> items, int page, int size, long totalElements, int totalPages) {
        public static AuditPage from(Page<AuditLog> result) {
            return new AuditPage(result.getContent().stream().map(AuditItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record AuditSummary(
            Instant from, Instant to, long total, Map<String, Long> byEventType,
            Map<String, Long> byStatus, Map<String, Long> bySeverity) {
        public static Map<String, Long> ordered(List<? extends CountValue> counts) {
            Map<String, Long> result = new LinkedHashMap<>();
            counts.forEach(value -> result.put(value.value(), value.total()));
            return result;
        }
    }

    public record CountValue(String value, long total) {}
}
