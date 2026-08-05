package com.oracle.banking.audit.service;

import com.oracle.banking.audit.dto.AuditDtos.AuditItem;
import com.oracle.banking.audit.dto.AuditDtos.AuditPage;
import com.oracle.banking.audit.dto.AuditDtos.AuditSummary;
import com.oracle.banking.audit.dto.AuditDtos.CountValue;
import com.oracle.banking.audit.entity.AuditLog;
import com.oracle.banking.audit.repository.AuditLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditQueryService {
    private static final Set<String> SORT_FIELDS = Set.of("occurredAt", "ingestedAt", "action", "status", "severity");
    private final AuditLogRepository repository;
    private final Duration maxRange;

    public AuditQueryService(
            AuditLogRepository repository,
            @Value("${audit.query.max-range-days}") long maxRangeDays) {
        this.repository = repository;
        this.maxRange = Duration.ofDays(maxRangeDays);
    }

    public AuditItem detail(String id) {
        return repository.findById(id).map(AuditItem::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit record not found"));
    }

    public AuditPage search(SearchFilter filter, int page, int size, String sortField, String direction) {
        validateRange(filter.from(), filter.to());
        String allowedSort = SORT_FIELDS.contains(sortField) ? sortField : "occurredAt";
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return AuditPage.from(repository.findAll(specification(filter),
                PageRequest.of(page, size, Sort.by(sortDirection, allowedSort).and(Sort.by(sortDirection, "ingestedAt")))));
    }

    public AuditSummary summary(Instant from, Instant to) {
        validateRange(from, to);
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(maxRange) : from;
        List<CountValue> eventTypes = repository.countByEventType(start, end).stream()
                .map(value -> new CountValue(value.getValue(), value.getTotal())).toList();
        List<CountValue> statuses = repository.countByOutcome(start, end).stream()
                .map(value -> new CountValue(value.getValue(), value.getTotal())).toList();
        List<CountValue> severities = repository.countBySeverity(start, end).stream()
                .map(value -> new CountValue(value.getValue(), value.getTotal())).toList();
        long total = statuses.stream().mapToLong(CountValue::total).sum();
        return new AuditSummary(start, end, total, AuditSummary.ordered(eventTypes),
                AuditSummary.ordered(statuses), AuditSummary.ordered(severities));
    }

    private Specification<AuditLog> specification(SearchFilter filter) {
        Specification<AuditLog> spec = (root, query, builder) -> builder.conjunction();
        spec = equal(spec, "actorUserId", filter.actorUserId());
        spec = equal(spec, "action", filter.action());
        spec = equal(spec, "sourceService", filter.sourceService());
        spec = equal(spec, "entityType", filter.entityType());
        spec = equal(spec, "referenceId", filter.referenceId());
        spec = equal(spec, "correlationId", filter.correlationId());
        spec = equal(spec, "status", filter.status());
        spec = equal(spec, "severity", filter.severity());
        if (filter.from() != null) spec = spec.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
        if (filter.to() != null) spec = spec.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
        return spec;
    }

    private Specification<AuditLog> equal(Specification<AuditLog> spec, String field, String value) {
        if (value == null || value.isBlank()) return spec;
        return spec.and((root, query, builder) -> builder.equal(root.get(field), value));
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && (from.isAfter(to) || Duration.between(from, to).compareTo(maxRange) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or excessive audit date range");
        }
    }

    public record SearchFilter(
            Instant from, Instant to, String actorUserId, String action, String sourceService,
            String entityType, String referenceId, String correlationId, String status, String severity) {}
}
