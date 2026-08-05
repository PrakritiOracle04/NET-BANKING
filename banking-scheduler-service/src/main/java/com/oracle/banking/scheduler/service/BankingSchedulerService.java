package com.oracle.banking.scheduler.service;

import com.oracle.banking.scheduler.dto.SchedulerDtos.DomainEvent;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ExecutionResponse;
import com.oracle.banking.scheduler.dto.SchedulerDtos.LoanMaintenanceWorkflowRequest;
import com.oracle.banking.scheduler.dto.SchedulerDtos.RunDueResponse;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ScheduleRequest;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ScheduleResponse;
import com.oracle.banking.scheduler.dto.SchedulerDtos.ScheduledBillPaymentWorkflowRequest;
import com.oracle.banking.scheduler.dto.SchedulerDtos.WorkflowResponse;
import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ExecutionStatus;
import com.oracle.banking.scheduler.entity.ScheduleExecution;
import com.oracle.banking.scheduler.entity.ScheduleOperationType;
import com.oracle.banking.scheduler.entity.ScheduleStatus;
import com.oracle.banking.scheduler.entity.ScheduleType;
import com.oracle.banking.scheduler.event.SchedulerEventPublisher;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.BadRequest;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.Conflict;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.DownstreamFailure;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.Forbidden;
import com.oracle.banking.scheduler.exception.SchedulerExceptions.NotFound;
import com.oracle.banking.scheduler.repository.BankingScheduleRepository;
import com.oracle.banking.scheduler.repository.ScheduleExecutionRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BankingSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(BankingSchedulerService.class);

    private final BankingScheduleRepository schedules;
    private final ScheduleExecutionRepository executions;
    private final RecurrenceCalculator recurrence;
    private final RestClient workflowClient;
    private final SchedulerEventPublisher events;
    private final NotificationRecipientClient recipients;
    private final String internalApiKey;
    private final ZoneId businessZone;
    private final String reminderScanTime;
    private final String overdueScanTime;
    private final int defaultMaxRetries;

    public BankingSchedulerService(
            BankingScheduleRepository schedules,
            ScheduleExecutionRepository executions,
            RecurrenceCalculator recurrence,
            RestClient.Builder restClientBuilder,
            SchedulerEventPublisher events,
            NotificationRecipientClient recipients,
            @Value("${services.banking-workflow-service-url}") String workflowServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            @Value("${banking.business-timezone}") String businessTimezone,
            @Value("${banking.system-schedules.emi-reminder-time}") String reminderScanTime,
            @Value("${banking.system-schedules.loan-overdue-time}") String overdueScanTime,
            @Value("${banking.schedules.default-max-retries}") int defaultMaxRetries) {
        this.schedules = schedules;
        this.executions = executions;
        this.recurrence = recurrence;
        this.workflowClient = restClientBuilder.baseUrl(workflowServiceUrl).build();
        this.events = events;
        this.recipients = recipients;
        this.internalApiKey = internalApiKey;
        this.businessZone = ZoneId.of(businessTimezone);
        this.reminderScanTime = reminderScanTime;
        this.overdueScanTime = overdueScanTime;
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public synchronized void seedSystemSchedules() {
        seedSystem(ScheduleOperationType.EMI_REMINDER_SCAN, reminderScanTime, "Daily EMI reminder maintenance scan");
        seedSystem(ScheduleOperationType.LOAN_OVERDUE_SCAN, overdueScanTime, "Daily loan overdue maintenance scan");
    }

    @Transactional
    public ScheduleResponse create(String userId, ScheduleRequest request) {
        validateBillPaymentRequest(request);
        Instant startAt = request.startAt();
        String timezone = request.timezone();
        BankingSchedule schedule = new BankingSchedule(
                userId,
                ScheduleOperationType.BILL_PAYMENT,
                request.scheduleType(),
                request.sourceAccountId(),
                request.customerBillerId(),
                request.amount(),
                request.description(),
                timezone,
                startAt,
                recurrence.firstExecution(startAt, timezone),
                request.endAt(),
                recurrence.requestedDay(startAt, timezone, request.scheduleType()),
                request.maxRetries() == null ? defaultMaxRetries : request.maxRetries(),
                false);
        BankingSchedule saved = schedules.save(schedule);
        events.lifecycle("CREATED", saved);
        return ScheduleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(String userId, boolean admin, String customerUserId, ScheduleStatus status) {
        List<BankingSchedule> rows;
        if (admin && customerUserId != null && status != null) {
            rows = schedules.findByCustomerUserIdAndStatusOrderByCreatedAtDesc(customerUserId, status);
        } else if (admin && customerUserId != null) {
            rows = schedules.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId);
        } else if (admin && status != null) {
            rows = schedules.findByStatusOrderByCreatedAtDesc(status);
        } else if (admin) {
            rows = schedules.findAllByOrderByCreatedAtDesc();
        } else if (status != null) {
            rows = schedules.findByCustomerUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            rows = schedules.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        }
        return rows.stream().map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse details(String id, String userId, boolean admin) {
        return ScheduleResponse.from(ownedOrAdmin(id, userId, admin));
    }

    @Transactional(readOnly = true)
    public List<ExecutionResponse> executions(String id, String userId, boolean admin) {
        BankingSchedule schedule = ownedOrAdmin(id, userId, admin);
        return executions.findByScheduleOrderByScheduledForDesc(schedule).stream()
                .map(ExecutionResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleResponse update(String id, String userId, ScheduleRequest request) {
        validateBillPaymentRequest(request);
        BankingSchedule schedule = ownedMutable(id, userId);
        schedule.updateBillPayment(
                request.scheduleType(),
                request.sourceAccountId(),
                request.customerBillerId(),
                request.amount(),
                request.description(),
                request.timezone(),
                request.startAt(),
                recurrence.firstExecution(request.startAt(), request.timezone()),
                request.endAt(),
                recurrence.requestedDay(request.startAt(), request.timezone(), request.scheduleType()),
                request.maxRetries() == null ? defaultMaxRetries : request.maxRetries());
        BankingSchedule saved = schedules.save(schedule);
        events.lifecycle("UPDATED", saved);
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public ScheduleResponse pause(String id, String userId) {
        BankingSchedule schedule = ownedMutable(id, userId);
        if (schedule.getStatus() != ScheduleStatus.ACTIVE) throw new Conflict("Only active schedules can be paused");
        schedule.pause();
        BankingSchedule saved = schedules.save(schedule);
        events.lifecycle("PAUSED", saved);
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public ScheduleResponse resume(String id, String userId) {
        BankingSchedule schedule = ownedMutable(id, userId);
        if (schedule.getStatus() != ScheduleStatus.PAUSED) throw new Conflict("Only paused schedules can be resumed");
        Instant next = schedule.getNextExecutionAt().isBefore(Instant.now())
                ? Instant.now()
                : schedule.getNextExecutionAt();
        schedule.resume(next);
        BankingSchedule saved = schedules.save(schedule);
        events.lifecycle("RESUMED", saved);
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public void cancel(String id, String userId) {
        BankingSchedule schedule = ownedMutable(id, userId);
        schedule.cancel();
        schedules.save(schedule);
        events.lifecycle("CANCELLED", schedule);
    }

    @Scheduled(fixedDelayString = "${banking.schedules.scan-delay-ms}")
    public void scanDueSchedules() {
        try {
            runDue();
        } catch (RuntimeException exception) {
            log.warn("Scheduler due scan failed");
        }
    }

    @Transactional
    public RunDueResponse runDue() {
        Instant now = Instant.now();
        int claimed = 0;
        int succeeded = 0;
        int retrying = 0;
        int failed = 0;

        for (BankingSchedule schedule : schedules.findTop50ByStatusAndNextExecutionAtLessThanEqualOrderByNextExecutionAtAsc(ScheduleStatus.ACTIVE, now)) {
            ScheduleExecution execution = claim(schedule, schedule.getNextExecutionAt());
            if (execution == null) continue;
            claimed++;
            ExecutionOutcome outcome = execute(schedule, execution);
            if (outcome == ExecutionOutcome.SUCCEEDED) succeeded++;
            if (outcome == ExecutionOutcome.RETRYING) retrying++;
            if (outcome == ExecutionOutcome.FAILED) failed++;
        }

        for (ScheduleExecution execution : executions.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(ExecutionStatus.RETRY_WAIT, now)) {
            ExecutionOutcome outcome = execute(execution.getSchedule(), execution);
            claimed++;
            if (outcome == ExecutionOutcome.SUCCEEDED) succeeded++;
            if (outcome == ExecutionOutcome.RETRYING) retrying++;
            if (outcome == ExecutionOutcome.FAILED) failed++;
        }

        return new RunDueResponse(claimed, succeeded, retrying, failed);
    }

    private ExecutionOutcome execute(BankingSchedule schedule, ScheduleExecution execution) {
        try {
            execution.running();
            executions.saveAndFlush(execution);
            events.triggered(event("schedule-triggered", schedule, execution, "RUNNING", "Schedule occurrence was triggered."));

            WorkflowResponse response = callWorkflow(schedule, execution);
            String reference = response == null || response.data() == null ? null : Objects.toString(response.data().get("referenceNumber"), null);
            execution.succeeded(reference, response == null ? "Workflow completed" : response.message());
            executions.save(execution);

            if (execution.getScheduledFor().equals(schedule.getNextExecutionAt())) {
                schedule.next(recurrence.next(schedule, execution.getScheduledFor()));
                schedules.save(schedule);
            }
            events.completed(event("schedule-completed", schedule, execution, "SUCCEEDED", "Schedule occurrence completed."));
            return ExecutionOutcome.SUCCEEDED;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                terminalFailure(schedule, execution, "Workflow rejected schedule occurrence");
                return ExecutionOutcome.FAILED;
            }
            return retryableFailure(schedule, execution, exception.getMessage());
        } catch (RestClientException exception) {
            return retryableFailure(schedule, execution, exception.getMessage());
        } catch (RuntimeException exception) {
            return retryableFailure(schedule, execution, exception.getMessage());
        }
    }

    private WorkflowResponse callWorkflow(BankingSchedule schedule, ScheduleExecution execution) {
        if (schedule.getOperationType() == ScheduleOperationType.BILL_PAYMENT) {
            return workflowClient.post()
                    .uri("/internal/workflows/scheduled-bill-payments")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new ScheduledBillPaymentWorkflowRequest(
                            schedule.getCustomerUserId(),
                            schedule.getScheduleId(),
                            execution.getScheduledFor(),
                            execution.getWorkflowIdempotencyKey(),
                            schedule.getSourceAccountId(),
                            schedule.getCustomerBillerId(),
                            schedule.getAmount(),
                            schedule.getDescription()))
                    .retrieve()
                    .body(WorkflowResponse.class);
        }
        LocalDate businessDate = execution.getScheduledFor().atZone(businessZone).toLocalDate();
        return workflowClient.post()
                .uri("/internal/workflows/loan-maintenance")
                .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                .body(new LoanMaintenanceWorkflowRequest(
                        schedule.getOperationType(),
                        execution.getScheduledFor(),
                        businessDate,
                        execution.getWorkflowIdempotencyKey()))
                .retrieve()
                .body(WorkflowResponse.class);
    }

    private ExecutionOutcome retryableFailure(BankingSchedule schedule, ScheduleExecution execution, String reason) {
        if (execution.getAttemptCount() >= schedule.getMaxRetries()) {
            terminalFailure(schedule, execution, reason);
            return ExecutionOutcome.FAILED;
        }
        execution.retryAt(reason, Instant.now().plusSeconds((long) execution.getAttemptCount() * 60));
        executions.save(execution);
        return ExecutionOutcome.RETRYING;
    }

    private void terminalFailure(BankingSchedule schedule, ScheduleExecution execution, String reason) {
        execution.failed(reason);
        executions.save(execution);
        if (schedule.getScheduleType() == ScheduleType.ONE_TIME) {
            schedule.failed();
        } else if (execution.getScheduledFor().equals(schedule.getNextExecutionAt())) {
            schedule.next(recurrence.next(schedule, execution.getScheduledFor()));
        }
        schedules.save(schedule);
        events.failed(event("schedule-failed", schedule, execution, "FAILED", "Schedule occurrence failed."));
    }

    private ScheduleExecution claim(BankingSchedule schedule, Instant scheduledFor) {
        String key = workflowKey(schedule, scheduledFor);
        try {
            return executions.findByScheduleAndScheduledFor(schedule, scheduledFor)
                    .orElseGet(() -> executions.saveAndFlush(new ScheduleExecution(schedule, scheduledFor, key)));
        } catch (DataIntegrityViolationException exception) {
            return null;
        }
    }

    private String workflowKey(BankingSchedule schedule, Instant scheduledFor) {
        if (schedule.getOperationType() == ScheduleOperationType.EMI_REMINDER_SCAN) {
            LocalDate date = scheduledFor.atZone(businessZone).toLocalDate();
            return "system:emi-reminder:" + date;
        }
        if (schedule.getOperationType() == ScheduleOperationType.LOAN_OVERDUE_SCAN) {
            LocalDate date = scheduledFor.atZone(businessZone).toLocalDate();
            return "system:loan-overdue:" + date;
        }
        return "schedule:" + schedule.getScheduleId() + ":" + scheduledFor;
    }

    private void seedSystem(ScheduleOperationType operationType, String localTime, String description) {
        List<BankingSchedule> existing = schedules
                .findByOperationTypeAndSystemOwnedTrueOrderByCreatedAtAsc(operationType);
        if (!existing.isEmpty()) {
            BankingSchedule canonical = existing.get(0);
            for (int index = 1; index < existing.size(); index++) {
                existing.get(index).retireDuplicateSystemSchedule();
            }
            canonical.assignSystemKey();
            try {
                schedules.saveAllAndFlush(existing);
            } catch (DataIntegrityViolationException exception) {
                log.info("System schedule {} was seeded by another runner", operationType);
            }
            return;
        }
        if (schedules.findBySystemKey(operationType.name()).isPresent()) return;
        Instant first = recurrence.dailyAt(businessZone, localTime);
        if (first.isBefore(Instant.now())) {
            first = first.plusSeconds(24 * 60 * 60);
        }
        try {
            schedules.saveAndFlush(new BankingSchedule(
                    null,
                    operationType,
                    ScheduleType.DAILY,
                    null,
                    null,
                    null,
                    description,
                    businessZone.getId(),
                    first,
                    first,
                    null,
                    null,
                    defaultMaxRetries,
                    true));
        } catch (DataIntegrityViolationException exception) {
            log.info("System schedule {} was seeded by another runner", operationType);
        }
    }

    private void validateBillPaymentRequest(ScheduleRequest request) {
        ZoneId.of(request.timezone());
        if (request.scheduleType() == null) throw new BadRequest("Schedule type is required");
        if (request.endAt() != null && !request.endAt().isAfter(request.startAt())) throw new BadRequest("End time must be after start time");
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) throw new BadRequest("Amount must be positive");
    }

    private BankingSchedule ownedOrAdmin(String id, String userId, boolean admin) {
        BankingSchedule schedule = schedules.findById(id).orElseThrow(() -> new NotFound("Schedule not found"));
        if (!admin && (schedule.isSystemOwned() || !Objects.equals(schedule.getCustomerUserId(), userId))) {
            throw new NotFound("Schedule not found");
        }
        return schedule;
    }

    private BankingSchedule ownedMutable(String id, String userId) {
        BankingSchedule schedule = schedules.findById(id).orElseThrow(() -> new NotFound("Schedule not found"));
        if (schedule.isSystemOwned()) throw new Forbidden("System schedules are protected");
        if (!Objects.equals(schedule.getCustomerUserId(), userId)) throw new NotFound("Schedule not found");
        if (schedule.getStatus() == ScheduleStatus.COMPLETED
                || schedule.getStatus() == ScheduleStatus.FAILED
                || schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new Conflict("Terminal schedules cannot be changed");
        }
        return schedule;
    }

    private DomainEvent event(String eventType, BankingSchedule schedule, ScheduleExecution execution, String status, String message) {
        String recipient = schedule.isSystemOwned()
                ? null
                : recipients.emailOrNull(schedule.getCustomerUserId());
        String templateName = switch (eventType) {
            case "schedule-triggered" -> "SCHEDULE_TRIGGERED";
            case "schedule-completed" -> "SCHEDULE_COMPLETED";
            case "schedule-failed" -> "SCHEDULE_FAILED";
            default -> "GENERIC_NOTIFICATION";
        };
        return new DomainEvent(
                eventType,
                execution.getWorkflowIdempotencyKey(),
                schedule.getScheduleId(),
                schedule.getCustomerUserId(),
                schedule.getOperationType().name(),
                execution.getScheduledFor(),
                status,
                Instant.now(),
                recipient,
                templateName,
                Map.of("message", message));
    }

    private enum ExecutionOutcome {
        SUCCEEDED,
        RETRYING,
        FAILED
    }
}
