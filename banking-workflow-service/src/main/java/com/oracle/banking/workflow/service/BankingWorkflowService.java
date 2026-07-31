package com.oracle.banking.workflow.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.workflow.dto.WorkflowDtos.BeneficiaryVerificationRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.BeneficiaryVerificationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DomainEvent;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalAccountValidationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.MoneyMovementRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.RecordTransactionRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransactionResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.TransferResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.WithdrawResponse;
import com.oracle.banking.workflow.entity.WorkflowSaga;
import com.oracle.banking.workflow.entity.WorkflowStatus;
import com.oracle.banking.workflow.entity.WorkflowType;
import com.oracle.banking.workflow.exception.WorkflowExceptions.BadRequest;
import com.oracle.banking.workflow.exception.WorkflowExceptions.CompensationPending;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Conflict;
import com.oracle.banking.workflow.exception.WorkflowExceptions.DownstreamFailure;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Forbidden;
import com.oracle.banking.workflow.repository.WorkflowSagaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BankingWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(BankingWorkflowService.class);

    private final RestClient accountClient;
    private final RestClient beneficiaryClient;
    private final RestClient transactionClient;
    private final WorkflowEventPublisher events;
    private final WorkflowSagaRepository sagas;
    private final String internalApiKey;
    private final NotificationRecipientClient recipients;

    public BankingWorkflowService(RestClient.Builder restClientBuilder, WorkflowEventPublisher events, WorkflowSagaRepository sagas, NotificationRecipientClient recipients,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.beneficiary-service-url}") String beneficiaryServiceUrl,
            @Value("${services.transaction-service-url}") String transactionServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.accountClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.beneficiaryClient = restClientBuilder.baseUrl(beneficiaryServiceUrl).build();
        this.transactionClient = restClientBuilder.baseUrl(transactionServiceUrl).build();
        this.events = events;
        this.sagas = sagas;
        this.recipients = recipients;
        this.internalApiKey = internalApiKey;
    }

    public DepositResponse deposit(String userId, boolean admin, String idempotencyKey, DepositRequest request) {
        InternalAccountValidationResponse account = validateAccount(request.accountId());
        requireOwnerOrAdmin(account, userId, admin);
        requireActive(account);
        WorkflowSaga saga = begin(account.customerUserId(), idempotencyKey, WorkflowType.DEPOSIT, "DEP", request.accountId(), null, request.amount(), request.description());
        if (saga.getStatus() == WorkflowStatus.COMPLETED) return depositResponse(saga);
        try {
            String movementReference = saga.getReferenceNumber() + ":CREDIT";
            saga.sourceMovementPlanned(movementReference);
            save(saga);
            credit(account.accountId(), request.amount(), movementReference, request.description());
            saga.sourceMoved(movementReference);
            save(saga);
            saga.debitTransactionPlanned(saga.getReferenceNumber());
            save(saga);
            TransactionResponse transaction = record(account, "DEPOSIT", saga.getReferenceNumber(), "DEPOSIT", request.amount(), "CREDIT", request.description());
            saga.debitTransactionRecorded(saga.getReferenceNumber(), transaction.transactionId());
            saga.transactionsRecorded();
            saga.complete();
            save(saga);
            events.accountCredited(event("account-credited", saga, account.accountId()));
            events.transactionCreated(event("transaction-created", saga, account.accountId()));
            return depositResponse(saga);
        } catch (RuntimeException ex) {
            throw fail(saga, ex);
        }
    }

    public WithdrawResponse withdraw(String userId, boolean admin, String idempotencyKey, WithdrawRequest request) {
        InternalAccountValidationResponse account = validateAccount(request.accountId());
        requireOwnerOrAdmin(account, userId, admin);
        requireActive(account);
        requireSufficientBalance(account, request.amount());
        WorkflowSaga saga = begin(account.customerUserId(), idempotencyKey, WorkflowType.WITHDRAWAL, "WDR", request.accountId(), null, request.amount(), request.description());
        if (saga.getStatus() == WorkflowStatus.COMPLETED) return withdrawResponse(saga);
        try {
            String movementReference = saga.getReferenceNumber() + ":DEBIT";
            saga.sourceMovementPlanned(movementReference);
            save(saga);
            debit(account.accountId(), request.amount(), movementReference, request.description());
            saga.sourceMoved(movementReference);
            save(saga);
            saga.debitTransactionPlanned(saga.getReferenceNumber());
            save(saga);
            TransactionResponse transaction = record(account, "WITHDRAWAL", saga.getReferenceNumber(), "WITHDRAWAL", request.amount(), "DEBIT", request.description());
            saga.debitTransactionRecorded(saga.getReferenceNumber(), transaction.transactionId());
            saga.transactionsRecorded();
            saga.complete();
            save(saga);
            events.accountDebited(event("account-debited", saga, account.accountId()));
            events.transactionCreated(event("transaction-created", saga, account.accountId()));
            return withdrawResponse(saga);
        } catch (RuntimeException ex) {
            throw fail(saga, ex);
        }
    }

    public TransferResponse transfer(String userId, boolean admin, String idempotencyKey, TransferRequest request) {
        InternalAccountValidationResponse source = validateAccount(request.sourceAccountId());
        InternalAccountValidationResponse destination = validateAccountByNumber(request.destinationAccountNumber());
        requireOwnerOrAdmin(source, userId, admin);
        requireActive(source);
        requireActive(destination);
        requireDifferentAccounts(source, destination);
        requireSufficientBalance(source, request.amount());
        verifyBeneficiary(source.customerUserId(), destination.accountNumber());
        WorkflowSaga saga = begin(source.customerUserId(), idempotencyKey, WorkflowType.TRANSFER, "TRF", request.sourceAccountId(), request.destinationAccountNumber(), request.amount(), request.description());
        if (saga.getStatus() == WorkflowStatus.COMPLETED) return transferResponse(saga);
        try {
            String sourceMovement = saga.getReferenceNumber() + ":SOURCE:DEBIT";
            saga.sourceMovementPlanned(sourceMovement);
            save(saga);
            debit(source.accountId(), request.amount(), sourceMovement, request.description());
            saga.sourceMoved(sourceMovement);
            save(saga);

            String destinationMovement = saga.getReferenceNumber() + ":DESTINATION:CREDIT";
            saga.destinationMovementPlanned(destination.accountId(), destinationMovement);
            save(saga);
            credit(destination.accountId(), request.amount(), destinationMovement, request.description());
            saga.destinationMoved(destination.accountId(), destinationMovement);
            save(saga);

            saga.debitTransactionPlanned(saga.getReferenceNumber());
            save(saga);
            TransactionResponse debitTransaction = record(source, "TRANSFER", saga.getReferenceNumber(), "TRANSFER", request.amount(), "DEBIT", request.description());
            saga.debitTransactionRecorded(saga.getReferenceNumber(), debitTransaction.transactionId());
            save(saga);
            String creditReference = saga.getReferenceNumber() + "-CR";
            saga.creditTransactionPlanned(creditReference);
            save(saga);
            TransactionResponse creditTransaction = record(destination, "TRANSFER", creditReference, "TRANSFER", request.amount(), "CREDIT", request.description());
            saga.creditTransactionRecorded(creditReference, creditTransaction.transactionId());
            saga.transactionsRecorded();
            saga.complete();
            save(saga);

            events.accountDebited(event("account-debited", saga, source.accountId()));
            events.accountCredited(event("account-credited", saga, destination.accountId()));
            events.transactionCreated(event("transaction-created", saga, source.accountId()));
            return transferResponse(saga);
        } catch (RuntimeException ex) {
            throw fail(saga, ex);
        }
    }

    @Scheduled(fixedDelayString = "${banking.saga.recovery-delay-ms}")
    public void retryPendingCompensations() {
        sagas.findByStatus(WorkflowStatus.COMPENSATION_PENDING).forEach(saga -> {
            if (compensate(saga)) {
                log.info("Recovered compensation for workflow {}", saga.getWorkflowId());
            }
        });
    }

    private WorkflowSaga begin(String customerUserId, String idempotencyKey, WorkflowType type, String prefix, String sourceAccountId,
            String destinationAccountNumber, BigDecimal amount, String description) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BadRequest("Idempotency-Key is required");
        WorkflowSaga existing = sagas.findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(customerUserId, idempotencyKey, type).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == WorkflowStatus.COMPLETED) return existing;
            if (existing.getStatus() == WorkflowStatus.COMPENSATION_PENDING) {
                throw new CompensationPending("Workflow " + existing.getReferenceNumber() + " is awaiting compensation");
            }
            throw new Conflict("Idempotency key was already used by workflow " + existing.getReferenceNumber());
        }
        return save(new WorkflowSaga(customerUserId, idempotencyKey, type, reference(prefix), sourceAccountId, destinationAccountNumber, amount, description));
    }

    private RuntimeException fail(WorkflowSaga saga, RuntimeException cause) {
        if (!saga.hasMutation()) {
            saga.fail(cause.getMessage());
            save(saga);
            return cause;
        }
        if (!compensate(saga)) {
            return new CompensationPending("Workflow " + saga.getReferenceNumber() + " requires compensation recovery");
        }
        return cause;
    }

    private boolean compensate(WorkflowSaga saga) {
        saga.compensating();
        save(saga);
        boolean successful = true;
        successful &= attempt("reverse credit transaction", () -> reverseTransaction(saga.getCreditTransactionReference()));
        successful &= attempt("reverse debit transaction", () -> reverseTransaction(saga.getDebitTransactionReference()));
        successful &= attempt("reverse destination movement", () -> reverseMovement(saga.getDestinationAccountId(), saga.getDestinationMovementReference()));
        successful &= attempt("reverse source movement", () -> reverseMovement(saga.getSourceAccountId(), saga.getSourceMovementReference()));
        if (successful) {
            saga.compensated();
        } else {
            saga.compensationPending("One or more compensation steps failed");
        }
        save(saga);
        return successful;
    }

    private boolean attempt(String action, Runnable operation) {
        try {
            operation.run();
            return true;
        } catch (RuntimeException ex) {
            log.error("Saga compensation failed while attempting {}", action, ex);
            return false;
        }
    }

    private InternalAccountValidationResponse validateAccount(String accountId) {
        try {
            return accountClient.get().uri("/internal/accounts/{id}/validate", accountId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey).retrieve()
                    .body(InternalAccountValidationResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Account validation failed");
        }
    }

    private InternalAccountValidationResponse validateAccountByNumber(String accountNumber) {
        try {
            return accountClient.get().uri("/internal/accounts/number/{accountNumber}/validate", accountNumber)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey).retrieve()
                    .body(InternalAccountValidationResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Destination account validation failed");
        }
    }

    private void verifyBeneficiary(String customerUserId, String destinationAccountNumber) {
        try {
            BeneficiaryVerificationResponse response = beneficiaryClient.post().uri("/internal/beneficiaries/verify-transfer")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new BeneficiaryVerificationRequest(customerUserId, destinationAccountNumber)).retrieve()
                    .body(BeneficiaryVerificationResponse.class);
            if (response == null || !response.verified()) throw new BadRequest("Beneficiary is not verified");
        } catch (BadRequest ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) throw new BadRequest("Beneficiary is not verified");
            throw new DownstreamFailure("Beneficiary verification failed");
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Beneficiary verification failed");
        }
    }

    private void credit(String accountId, BigDecimal amount, String reference, String description) {
        movement(accountId, "/internal/accounts/{id}/credit", amount, reference, description);
    }

    private void debit(String accountId, BigDecimal amount, String reference, String description) {
        movement(accountId, "/internal/accounts/{id}/debit", amount, reference, description);
    }

    private void movement(String accountId, String uri, BigDecimal amount, String reference, String description) {
        try {
            accountClient.post().uri(uri, accountId).header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new MoneyMovementRequest(amount, reference, description)).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Account balance update failed");
        }
    }

    private void reverseMovement(String accountId, String reference) {
        if (accountId == null || reference == null) return;
        try {
            accountClient.post().uri("/internal/accounts/{id}/movements/{reference}/reverse", accountId, reference)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return;
            throw new DownstreamFailure("Account compensation failed");
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Account compensation failed");
        }
    }

    private TransactionResponse record(InternalAccountValidationResponse account, String type, String reference, String referenceType,
            BigDecimal amount, String debitCredit, String description) {
        try {
            return transactionClient.post().uri("/internal/transactions").header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new RecordTransactionRequest(account.accountId(), account.accountNumber(), account.customerUserId(), type,
                            reference, referenceType, amount, debitCredit, "SUCCESS", description, Instant.now()))
                    .retrieve().body(TransactionResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Transaction record creation failed");
        }
    }

    private void reverseTransaction(String reference) {
        if (reference == null) return;
        try {
            transactionClient.post().uri("/internal/transactions/{reference}/reverse", reference)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return;
            throw new DownstreamFailure("Transaction compensation failed");
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Transaction compensation failed");
        }
    }

    private WorkflowSaga save(WorkflowSaga saga) {
        return sagas.saveAndFlush(saga);
    }

    private void requireOwnerOrAdmin(InternalAccountValidationResponse account, String userId, boolean admin) {
        if (!admin && !account.customerUserId().equals(userId)) throw new Forbidden("Account does not belong to authenticated customer");
    }

    private void requireActive(InternalAccountValidationResponse account) {
        if (!account.active()) throw new BadRequest("Only active accounts can be used for banking operations");
    }

    private void requireSufficientBalance(InternalAccountValidationResponse account, BigDecimal amount) {
        if (account.availableBalance().compareTo(amount) < 0) throw new BadRequest("Insufficient balance");
    }

    private void requireDifferentAccounts(InternalAccountValidationResponse source, InternalAccountValidationResponse destination) {
        if (source.accountId().equals(destination.accountId())) throw new BadRequest("Source and destination accounts must be different");
    }

    private DomainEvent event(String eventType, WorkflowSaga saga, String accountId) {
        try {
            String email = recipients.email(saga.getCustomerUserId());
            return new DomainEvent(eventType, saga.getReferenceNumber(), accountId, saga.getAmount(), "SUCCESS", Instant.now(), email,
                    "GENERIC_NOTIFICATION", Map.of("message", "Your banking operation " + saga.getReferenceNumber() + " completed successfully."));
        } catch (RuntimeException ex) {
            log.warn("Notification event was skipped for workflow {}", saga.getReferenceNumber());
            return null;
        }
    }

    private DepositResponse depositResponse(WorkflowSaga saga) {
        return new DepositResponse(saga.getReferenceNumber(), saga.getSourceAccountId(), saga.getAmount(), "SUCCESS", saga.getDebitTransactionId());
    }

    private WithdrawResponse withdrawResponse(WorkflowSaga saga) {
        return new WithdrawResponse(saga.getReferenceNumber(), saga.getSourceAccountId(), saga.getAmount(), "SUCCESS", saga.getDebitTransactionId());
    }

    private TransferResponse transferResponse(WorkflowSaga saga) {
        return new TransferResponse(saga.getReferenceNumber(), saga.getSourceAccountId(), saga.getDestinationAccountId(), saga.getAmount(), "SUCCESS",
                saga.getDebitTransactionId(), saga.getCreditTransactionId());
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
