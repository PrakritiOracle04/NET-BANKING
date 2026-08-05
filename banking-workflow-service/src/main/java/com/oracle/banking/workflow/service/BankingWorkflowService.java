package com.oracle.banking.workflow.service;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.workflow.dto.WorkflowDtos.BeneficiaryVerificationRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.BeneficiaryVerificationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.DepositResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.DomainEvent;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalAccountValidationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.MoneyMovementRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.OpenAccountRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.OpenAccountResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.CustomerOnboardingStatus;
import com.oracle.banking.workflow.dto.WorkflowDtos.BranchResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.BillPaymentWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.BillPaymentWorkflowResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalBillerValidationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalBillPaymentResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalCompleteBillPaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalCompleteLoanRepaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalCreateBillPaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalCreateLoanRepaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalFailBillPaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalFailLoanRepaymentRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalLoanRepaymentResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalLoanValidationResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalOpenAccountRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.InternalOpenAccountResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanRepaymentWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanRepaymentWorkflowResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanMaintenanceResponse;
import com.oracle.banking.workflow.dto.WorkflowDtos.LoanMaintenanceWorkflowRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.RecordTransactionRequest;
import com.oracle.banking.workflow.dto.WorkflowDtos.ScheduledBillPaymentWorkflowRequest;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private final RestClient customerClient;
    private final RestClient branchClient;
    private final RestClient billPaymentClient;
    private final RestClient loanClient;
    private final WorkflowEventPublisher events;
    private final WorkflowSagaRepository sagas;
    private final String internalApiKey;
    private final NotificationRecipientClient recipients;

    public BankingWorkflowService(RestClient.Builder restClientBuilder, WorkflowEventPublisher events, WorkflowSagaRepository sagas, NotificationRecipientClient recipients,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.beneficiary-service-url}") String beneficiaryServiceUrl,
            @Value("${services.transaction-service-url}") String transactionServiceUrl,
            @Value("${services.customer-service-url}") String customerServiceUrl,
            @Value("${services.branch-service-url}") String branchServiceUrl,
            @Value("${services.billpayment-service-url}") String billPaymentServiceUrl,
            @Value("${services.loan-service-url}") String loanServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.accountClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.beneficiaryClient = restClientBuilder.baseUrl(beneficiaryServiceUrl).build();
        this.transactionClient = restClientBuilder.baseUrl(transactionServiceUrl).build();
        this.customerClient = restClientBuilder.baseUrl(customerServiceUrl).build();
        this.branchClient = restClientBuilder.baseUrl(branchServiceUrl).build();
        this.billPaymentClient = restClientBuilder.baseUrl(billPaymentServiceUrl).build();
        this.loanClient = restClientBuilder.baseUrl(loanServiceUrl).build();
        this.events = events;
        this.sagas = sagas;
        this.recipients = recipients;
        this.internalApiKey = internalApiKey;
    }

    public BillPaymentWorkflowResponse payBill(
            String userId,
            String idempotencyKey,
            BillPaymentWorkflowRequest request) {
        WorkflowSaga saga = beginBillPayment(userId, idempotencyKey, request);
        if (saga.getStatus() == WorkflowStatus.COMPLETED) return billPaymentResponse(saga);
        try {
            InternalAccountValidationResponse account = validateAccount(request.sourceAccountId());
            requireOwnerOrAdmin(account, userId, false);
            requireActive(account);
            requireSufficientBalance(account, request.amount());

            InternalBillerValidationResponse biller = validateRegisteredBiller(
                    request.customerBillerId(), userId);
            if (!biller.active()) throw new BadRequest("Registered biller must be active");

            saga.prerequisitesValidated();
            save(saga);

            InternalBillPaymentResponse pending = createPendingBillPayment(saga, request);
            saga.billPaymentCreated(pending.billPaymentId());
            save(saga);

            String movementReference = saga.getReferenceNumber() + ":DEBIT";
            saga.sourceMovementPlanned(movementReference);
            save(saga);
            debit(account.accountId(), request.amount(), movementReference, request.description());
            saga.sourceMoved(movementReference);
            save(saga);

            String transactionReference = saga.getReferenceNumber();
            saga.debitTransactionPlanned(transactionReference);
            save(saga);
            TransactionResponse transaction = record(
                    account,
                    "BILL_PAYMENT",
                    transactionReference,
                    "BILL_PAYMENT",
                    request.amount(),
                    "DEBIT",
                    request.description());
            saga.debitTransactionRecorded(transactionReference, transaction.transactionId());
            saga.transactionsRecorded();
            save(saga);

            completeBillPayment(saga.getBillPaymentId(), transaction.transactionId(), transactionReference);
            saga.complete();
            save(saga);
            events.workflowCompleted(saga);

            events.billPaymentSucceeded(billPaymentEvent(
                    "bill-payment-success",
                    saga,
                    "Your bill payment " + saga.getReferenceNumber() + " completed successfully."));
            return billPaymentResponse(saga);
        } catch (RuntimeException exception) {
            RuntimeException outcome = fail(saga, exception);
            if (saga.getStatus() == WorkflowStatus.COMPENSATED || saga.getStatus() == WorkflowStatus.FAILED) {
                events.billPaymentFailed(billPaymentEvent(
                        "bill-payment-failed",
                        saga,
                        "Your bill payment " + saga.getReferenceNumber() + " failed and no funds were retained."));
            }
            throw outcome;
        }
    }

    public BillPaymentWorkflowResponse payScheduledBill(ScheduledBillPaymentWorkflowRequest request) {
        return payBill(
                request.customerUserId(),
                request.idempotencyKey(),
                new BillPaymentWorkflowRequest(
                        request.sourceAccountId(),
                        request.customerBillerId(),
                        request.amount(),
                        request.description()));
    }

    public LoanMaintenanceResponse runLoanMaintenance(LoanMaintenanceWorkflowRequest request) {
        return switch (request.operationType()) {
            case "EMI_REMINDER_SCAN" -> runLoanMaintenanceEndpoint(
                    request,
                    "/internal/loans/maintenance/emi-reminders",
                    "EMI_REMINDER_SCAN");
            case "LOAN_OVERDUE_SCAN" -> runLoanMaintenanceEndpoint(
                    request,
                    "/internal/loans/maintenance/overdue",
                    "LOAN_OVERDUE_SCAN");
            default -> throw new BadRequest("Unsupported loan maintenance operation");
        };
    }

    public LoanRepaymentWorkflowResponse repayLoan(
            String userId,
            String idempotencyKey,
            String loanId,
            LoanRepaymentWorkflowRequest request) {
        WorkflowSaga saga = beginLoanRepayment(userId, idempotencyKey, loanId, request);
        if (saga.getStatus() == WorkflowStatus.COMPLETED) return loanRepaymentResponse(saga);
        try {
            InternalLoanValidationResponse loan = validateLoan(loanId, userId, request.amount());
            InternalAccountValidationResponse account = validateAccount(request.sourceAccountId());
            requireOwnerOrAdmin(account, userId, false);
            requireActive(account);
            requireSufficientBalance(account, request.amount());
            if (!Objects.equals(loan.customerUserId(), account.customerUserId())) {
                throw new Forbidden("Loan does not belong to source account customer");
            }
            if (!"ACTIVE".equals(loan.status()) && !"OVERDUE".equals(loan.status())) {
                throw new BadRequest("Only active or overdue loans can be repaid");
            }

            saga.prerequisitesValidated();
            save(saga);

            InternalLoanRepaymentResponse pending = createPendingLoanRepayment(saga, request);
            saga.loanRepaymentCreated(pending.loanRepaymentId());
            save(saga);

            String movementReference = saga.getReferenceNumber() + ":LOAN:DEBIT";
            saga.sourceMovementPlanned(movementReference);
            save(saga);
            debit(account.accountId(), request.amount(), movementReference, request.description());
            saga.sourceMoved(movementReference);
            save(saga);

            String transactionReference = saga.getReferenceNumber();
            saga.debitTransactionPlanned(transactionReference);
            save(saga);
            TransactionResponse transaction = record(
                    account,
                    "LOAN_REPAYMENT",
                    transactionReference,
                    "LOAN_REPAYMENT",
                    request.amount(),
                    "DEBIT",
                    request.description());
            saga.debitTransactionRecorded(transactionReference, transaction.transactionId());
            saga.transactionsRecorded();
            save(saga);

            completeLoanRepayment(saga.getLoanRepaymentId(), transaction.transactionId(), transactionReference);
            saga.complete();
            save(saga);
            events.workflowCompleted(saga);

            events.loanPaymentSucceeded(loanPaymentEvent(
                    "loan-payment-success",
                    saga,
                    "Your loan payment " + saga.getReferenceNumber() + " completed successfully."));
            events.accountDebited(event("account-debited", saga, account.accountId()));
            events.transactionCreated(event("transaction-created", saga, account.accountId()));
            return loanRepaymentResponse(saga);
        } catch (RuntimeException exception) {
            RuntimeException outcome = fail(saga, exception);
            if (saga.getStatus() == WorkflowStatus.COMPENSATED || saga.getStatus() == WorkflowStatus.FAILED) {
                events.loanPaymentFailed(loanPaymentEvent(
                        "loan-payment-failed",
                        saga,
                        "Your loan payment " + saga.getReferenceNumber() + " failed and no funds were retained."));
            }
            throw outcome;
        }
    }

    public OpenAccountResponse openAccount(String userId, String idempotencyKey, OpenAccountRequest request) {
        WorkflowSaga saga = beginAccountOpening(userId, idempotencyKey, request);
        if (saga.getStatus() == WorkflowStatus.COMPLETED) {
            return openAccountResponse(saga);
        }
        try {
            CustomerOnboardingStatus onboarding = customerOnboardingStatus(userId);
            if (!onboarding.profileComplete()) {
                throw new Conflict("Complete the customer profile before opening an account");
            }
            if (!"VERIFIED".equals(onboarding.kycStatus())) {
                throw new Conflict("KYC must be VERIFIED before opening an account");
            }
            if (!onboarding.eligibleForAccountOpening()) {
                throw new Conflict("Customer is not eligible for account opening");
            }
            BranchResponse branch = validateBranch(request.branchIfsc());
            saga.prerequisitesValidated();
            save(saga);
            InternalOpenAccountResponse account = createAccount(
                    userId,
                    request,
                    branch.ifsc(),
                    saga.getReferenceNumber());
            saga.accountCreated(account.accountId(), account.accountNumber(), account.primaryAccount());
            saga.complete();
            save(saga);
            events.workflowCompleted(saga);
            events.accountOpened(saga);
            return new OpenAccountResponse(
                    saga.getReferenceNumber(),
                    account.accountId(),
                    account.accountNumber(),
                    account.accountType(),
                    account.branchIfsc(),
                    account.status(),
                    account.primaryAccount());
        } catch (RuntimeException exception) {
            saga.fail(exception.getMessage());
            save(saga);
            events.workflowFailed(saga);
            throw exception;
        }
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
            events.workflowCompleted(saga);
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
            events.workflowCompleted(saga);
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
            events.workflowCompleted(saga);

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
                if (saga.getWorkflowType() == WorkflowType.BILL_PAYMENT) {
                    events.billPaymentFailed(billPaymentEvent(
                            "bill-payment-failed",
                            saga,
                            "Your bill payment " + saga.getReferenceNumber() + " failed and was reversed."));
                } else if (saga.getWorkflowType() == WorkflowType.LOAN_REPAYMENT) {
                    events.loanPaymentFailed(loanPaymentEvent(
                            "loan-payment-failed",
                            saga,
                            "Your loan payment " + saga.getReferenceNumber() + " failed and was reversed."));
                }
            }
        });
    }

    private WorkflowSaga begin(String customerUserId, String idempotencyKey, WorkflowType type, String prefix, String sourceAccountId,
            String destinationAccountNumber, BigDecimal amount, String description) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BadRequest("Idempotency-Key is required");
        WorkflowSaga existing = sagas.findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(customerUserId, idempotencyKey, type).orElse(null);
        if (existing != null) {
            if (!sameRequest(existing, sourceAccountId, destinationAccountNumber, amount, description)) {
                throw new Conflict("Idempotency key was already used with a different request");
            }
            if (existing.getStatus() == WorkflowStatus.COMPLETED) return existing;
            if (existing.getStatus() == WorkflowStatus.COMPENSATION_PENDING) {
                throw new CompensationPending("Workflow " + existing.getReferenceNumber() + " is awaiting compensation");
            }
            throw new Conflict("Idempotency key was already used by workflow " + existing.getReferenceNumber());
        }
        return save(new WorkflowSaga(customerUserId, idempotencyKey, type, reference(prefix), sourceAccountId, destinationAccountNumber, amount, description));
    }

    private boolean sameRequest(
            WorkflowSaga saga,
            String sourceAccountId,
            String destinationAccountNumber,
            BigDecimal amount,
            String description) {
        return Objects.equals(saga.getSourceAccountId(), sourceAccountId)
                && Objects.equals(saga.getDestinationAccountNumber(), destinationAccountNumber)
                && saga.getAmount().compareTo(amount) == 0
                && Objects.equals(saga.getDescription(), description);
    }

    private WorkflowSaga beginBillPayment(
            String userId,
            String idempotencyKey,
            BillPaymentWorkflowRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequest("Idempotency-Key is required");
        }
        WorkflowSaga existing = sagas.findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(
                userId, idempotencyKey, WorkflowType.BILL_PAYMENT).orElse(null);
        if (existing != null) {
            boolean sameRequest = Objects.equals(existing.getSourceAccountId(), request.sourceAccountId())
                    && Objects.equals(existing.getCustomerBillerId(), request.customerBillerId())
                    && existing.getAmount().compareTo(request.amount()) == 0
                    && Objects.equals(existing.getDescription(), request.description());
            if (!sameRequest) throw new Conflict("Idempotency key was already used with a different bill-payment request");
            if (existing.getStatus() == WorkflowStatus.COMPLETED) return existing;
            if (existing.getStatus() == WorkflowStatus.COMPENSATION_PENDING) {
                throw new CompensationPending(
                        "Workflow " + existing.getReferenceNumber() + " is awaiting compensation");
            }
            throw new Conflict("Idempotency key was already used by workflow " + existing.getReferenceNumber());
        }
        WorkflowSaga saga = new WorkflowSaga(
                userId,
                idempotencyKey,
                WorkflowType.BILL_PAYMENT,
                reference("BIL"),
                request.sourceAccountId(),
                null,
                request.amount(),
                request.description());
        saga.billPaymentRequested(request.customerBillerId());
        return save(saga);
    }

    private WorkflowSaga beginLoanRepayment(
            String userId,
            String idempotencyKey,
            String loanId,
            LoanRepaymentWorkflowRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequest("Idempotency-Key is required");
        }
        if (loanId == null || loanId.isBlank()) {
            throw new BadRequest("Loan id is required");
        }
        WorkflowSaga existing = sagas.findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(
                userId, idempotencyKey, WorkflowType.LOAN_REPAYMENT).orElse(null);
        if (existing != null) {
            boolean sameRequest = Objects.equals(existing.getSourceAccountId(), request.sourceAccountId())
                    && Objects.equals(existing.getLoanId(), loanId)
                    && existing.getAmount().compareTo(request.amount()) == 0
                    && Objects.equals(existing.getDescription(), request.description());
            if (!sameRequest) throw new Conflict("Idempotency key was already used with a different loan-repayment request");
            if (existing.getStatus() == WorkflowStatus.COMPLETED) return existing;
            if (existing.getStatus() == WorkflowStatus.COMPENSATION_PENDING) {
                throw new CompensationPending(
                        "Workflow " + existing.getReferenceNumber() + " is awaiting compensation");
            }
            throw new Conflict("Idempotency key was already used by workflow " + existing.getReferenceNumber());
        }
        WorkflowSaga saga = new WorkflowSaga(
                userId,
                idempotencyKey,
                WorkflowType.LOAN_REPAYMENT,
                reference("LNP"),
                request.sourceAccountId(),
                null,
                request.amount(),
                request.description());
        saga.loanRepaymentRequested(loanId);
        return save(saga);
    }

    private WorkflowSaga beginAccountOpening(
            String userId,
            String idempotencyKey,
            OpenAccountRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequest("Idempotency-Key is required");
        }
        WorkflowSaga existing = sagas.findByCustomerUserIdAndIdempotencyKeyAndWorkflowType(
                userId,
                idempotencyKey,
                WorkflowType.ACCOUNT_OPENING).orElse(null);
        if (existing != null) {
            if (!request.accountType().equals(existing.getAccountType())
                    || !request.branchIfsc().equals(existing.getBranchIfsc())) {
                throw new Conflict("Idempotency key was already used with a different account-opening request");
            }
            if (existing.getStatus() == WorkflowStatus.COMPLETED) {
                return existing;
            }
            if (existing.getStatus() == WorkflowStatus.COMPENSATION_PENDING) {
                throw new CompensationPending(
                        "Workflow " + existing.getReferenceNumber() + " is awaiting compensation");
            }
            existing.retry();
            return save(existing);
        }
        WorkflowSaga saga = new WorkflowSaga(
                userId,
                idempotencyKey,
                WorkflowType.ACCOUNT_OPENING,
                reference("AOP"),
                null,
                null,
                BigDecimal.ZERO,
                "Account opening");
        saga.accountOpeningRequested(request.accountType(), request.branchIfsc());
        return save(saga);
    }

    private CustomerOnboardingStatus customerOnboardingStatus(String userId) {
        try {
            CustomerOnboardingStatus response = customerClient.get()
                    .uri("/internal/customers/{userId}/onboarding-status", userId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(CustomerOnboardingStatus.class);
            if (response == null) {
                throw new DownstreamFailure("Customer eligibility check returned no data");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BadRequest("Customer profile was not found");
            }
            throw new DownstreamFailure("Customer eligibility check failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Customer eligibility check failed");
        }
    }

    private BranchResponse validateBranch(String ifsc) {
        try {
            BranchResponse response = branchClient.get()
                    .uri("/internal/branches/ifsc/{ifsc}", ifsc)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(BranchResponse.class);
            if (response == null) {
                throw new BadRequest("Branch was not found");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BadRequest("Branch IFSC was not found");
            }
            throw new DownstreamFailure("Branch validation failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Branch validation failed");
        }
    }

    private InternalOpenAccountResponse createAccount(
            String userId,
            OpenAccountRequest request,
            String validatedIfsc,
            String openingReference) {
        try {
            InternalOpenAccountResponse response = accountClient.post()
                    .uri("/internal/accounts/open")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalOpenAccountRequest(
                            userId,
                            request.accountType(),
                            validatedIfsc,
                            openingReference))
                    .retrieve()
                    .body(InternalOpenAccountResponse.class);
            if (response == null) {
                throw new DownstreamFailure("Account service returned no data");
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BadRequest("Account could not be opened");
            }
            throw new DownstreamFailure("Account creation failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Account creation failed");
        }
    }

    private InternalBillerValidationResponse validateRegisteredBiller(String customerBillerId, String userId) {
        try {
            InternalBillerValidationResponse response = billPaymentClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/billers/{id}/validate")
                            .queryParam("customerUserId", userId)
                            .build(customerBillerId))
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalBillerValidationResponse.class);
            if (response == null) throw new DownstreamFailure("Biller validation returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Registered biller could not be validated");
            throw new DownstreamFailure("Bill Payment Service is unavailable");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Bill Payment Service is unavailable");
        }
    }

    private InternalBillPaymentResponse createPendingBillPayment(
            WorkflowSaga saga,
            BillPaymentWorkflowRequest request) {
        try {
            InternalBillPaymentResponse response = billPaymentClient.post()
                    .uri("/internal/bill-payments")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalCreateBillPaymentRequest(
                            saga.getCustomerUserId(), request.customerBillerId(), request.sourceAccountId(),
                            request.amount(), saga.getReferenceNumber(), request.description()))
                    .retrieve()
                    .body(InternalBillPaymentResponse.class);
            if (response == null) throw new DownstreamFailure("Bill Payment Service returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Bill payment could not be created");
            throw new DownstreamFailure("Bill Payment Service is unavailable");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Bill Payment Service is unavailable");
        }
    }

    private void completeBillPayment(String id, String transactionId, String transactionReference) {
        try {
            billPaymentClient.put()
                    .uri("/internal/bill-payments/{id}/complete", id)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalCompleteBillPaymentRequest(transactionId, transactionReference))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Bill payment completion failed");
        }
    }

    private void cancelBillPayment(String id, String workflowReference, String reason) {
        try {
            billPaymentClient.put()
                    .uri(
                            id == null
                                    ? "/internal/bill-payments/workflow/{reference}/cancel"
                                    : "/internal/bill-payments/{reference}/cancel",
                            id == null ? workflowReference : id)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalFailBillPaymentRequest(
                            reason == null || reason.isBlank() ? "Workflow compensated" : reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404 && id == null) return;
            throw new DownstreamFailure("Bill payment compensation failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Bill payment compensation failed");
        }
    }

    private InternalLoanValidationResponse validateLoan(String loanId, String userId, BigDecimal amount) {
        try {
            InternalLoanValidationResponse response = loanClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/loans/{id}/validate")
                            .queryParam("customerUserId", userId)
                            .queryParam("amount", amount)
                            .build(loanId))
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalLoanValidationResponse.class);
            if (response == null) throw new DownstreamFailure("Loan validation returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Loan could not be validated");
            throw new DownstreamFailure("Loan Service is unavailable");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Loan Service is unavailable");
        }
    }

    private InternalLoanRepaymentResponse createPendingLoanRepayment(
            WorkflowSaga saga,
            LoanRepaymentWorkflowRequest request) {
        try {
            InternalLoanRepaymentResponse response = loanClient.post()
                    .uri("/internal/loan-repayments")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalCreateLoanRepaymentRequest(
                            saga.getLoanId(),
                            saga.getCustomerUserId(),
                            request.sourceAccountId(),
                            request.amount(),
                            saga.getReferenceNumber()))
                    .retrieve()
                    .body(InternalLoanRepaymentResponse.class);
            if (response == null) throw new DownstreamFailure("Loan Service returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Loan repayment could not be created");
            throw new DownstreamFailure("Loan Service is unavailable");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Loan Service is unavailable");
        }
    }

    private void completeLoanRepayment(String id, String transactionId, String transactionReference) {
        try {
            loanClient.put()
                    .uri("/internal/loan-repayments/{id}/complete", id)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalCompleteLoanRepaymentRequest(transactionId, transactionReference))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Loan repayment completion failed");
        }
    }

    private void reverseLoanRepayment(String id, String workflowReference, String reason) {
        try {
            loanClient.put()
                    .uri(
                            id == null
                                    ? "/internal/loan-repayments/workflow/{reference}/reverse"
                                    : "/internal/loan-repayments/{reference}/reverse",
                            id == null ? workflowReference : id)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new InternalFailLoanRepaymentRequest(
                            reason == null || reason.isBlank() ? "Workflow compensated" : reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404 && id == null) return;
            throw new DownstreamFailure("Loan repayment compensation failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Loan repayment compensation failed");
        }
    }

    private LoanMaintenanceResponse runLoanMaintenanceEndpoint(
            LoanMaintenanceWorkflowRequest request,
            String path,
            String operationType) {
        try {
            LoanMaintenanceResponse response = loanClient.post()
                    .uri(path)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new com.oracle.banking.workflow.dto.WorkflowDtos.InternalLoanMaintenanceRequest(
                            request.businessDate(),
                            request.idempotencyKey()))
                    .retrieve()
                    .body(LoanMaintenanceResponse.class);
            if (response == null) throw new DownstreamFailure("Loan maintenance returned no data");
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) throw new BadRequest("Loan maintenance request was rejected");
            throw new DownstreamFailure("Loan maintenance failed");
        } catch (RestClientException exception) {
            throw new DownstreamFailure("Loan maintenance failed");
        }
    }

    private RuntimeException fail(WorkflowSaga saga, RuntimeException cause) {
        if (!saga.hasMutation()) {
            saga.fail(cause.getMessage());
            save(saga);
            events.workflowFailed(saga);
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
        if (saga.getWorkflowType() == WorkflowType.LOAN_REPAYMENT) {
            successful &= attempt(
                    "reverse loan repayment",
                    () -> reverseLoanRepayment(
                            saga.getLoanRepaymentId(),
                            saga.getReferenceNumber(),
                            "Workflow was compensated"));
        }
        successful &= attempt("reverse credit transaction", () -> reverseTransaction(saga.getCreditTransactionReference()));
        successful &= attempt("reverse debit transaction", () -> reverseTransaction(saga.getDebitTransactionReference()));
        successful &= attempt("reverse destination movement", () -> reverseMovement(saga.getDestinationAccountId(), saga.getDestinationMovementReference()));
        successful &= attempt("reverse source movement", () -> reverseMovement(saga.getSourceAccountId(), saga.getSourceMovementReference()));
        if (saga.getWorkflowType() == WorkflowType.BILL_PAYMENT) {
            successful &= attempt(
                    "cancel bill payment",
                    () -> cancelBillPayment(
                            saga.getBillPaymentId(),
                            saga.getReferenceNumber(),
                            "Workflow was compensated"));
        }
        if (successful) {
            saga.compensated();
        } else {
            saga.compensationPending("One or more compensation steps failed");
        }
        save(saga);
        events.workflowCompensated(saga);
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
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) throw new BadRequest("Account could not be validated");
            throw new DownstreamFailure("Account validation failed");
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
        return new DomainEvent(
                eventType,
                saga.getReferenceNumber(),
                accountId,
                saga.getCustomerUserId(),
                saga.getWorkflowType().name(),
                saga.getAmount(),
                "SUCCESS",
                Instant.now(),
                recipientOrNull(saga),
                "GENERIC_NOTIFICATION",
                Map.of("message", "Your banking operation " + saga.getReferenceNumber() + " completed successfully."));
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

    private BillPaymentWorkflowResponse billPaymentResponse(WorkflowSaga saga) {
        return new BillPaymentWorkflowResponse(
                saga.getReferenceNumber(),
                saga.getBillPaymentId(),
                saga.getDebitTransactionId(),
                saga.getSourceAccountId(),
                saga.getAmount(),
                "SUCCESS");
    }

    private LoanRepaymentWorkflowResponse loanRepaymentResponse(WorkflowSaga saga) {
        return new LoanRepaymentWorkflowResponse(
                saga.getReferenceNumber(),
                saga.getLoanId(),
                saga.getLoanRepaymentId(),
                saga.getDebitTransactionId(),
                saga.getSourceAccountId(),
                saga.getAmount(),
                "SUCCESS");
    }

    private DomainEvent billPaymentEvent(String eventType, WorkflowSaga saga, String message) {
        return new DomainEvent(
                eventType,
                saga.getReferenceNumber(),
                saga.getSourceAccountId(),
                saga.getCustomerUserId(),
                saga.getWorkflowType().name(),
                saga.getAmount(),
                saga.getStatus().name(),
                Instant.now(),
                recipientOrNull(saga),
                "GENERIC_NOTIFICATION",
                Map.of("message", message));
    }

    private DomainEvent loanPaymentEvent(String eventType, WorkflowSaga saga, String message) {
        return new DomainEvent(
                eventType,
                saga.getReferenceNumber(),
                saga.getSourceAccountId(),
                saga.getCustomerUserId(),
                saga.getWorkflowType().name(),
                saga.getAmount(),
                saga.getStatus().name(),
                Instant.now(),
                recipientOrNull(saga),
                "GENERIC_NOTIFICATION",
                Map.of("message", message));
    }

    private String recipientOrNull(WorkflowSaga saga) {
        try {
            return recipients.email(saga.getCustomerUserId());
        } catch (RuntimeException exception) {
            log.warn("Workflow event has no notification recipient for reference {}", saga.getReferenceNumber());
            return null;
        }
    }

    private OpenAccountResponse openAccountResponse(WorkflowSaga saga) {
        return new OpenAccountResponse(
                saga.getReferenceNumber(),
                saga.getSourceAccountId(),
                saga.getAccountNumber(),
                saga.getAccountType(),
                saga.getBranchIfsc(),
                "ACTIVE",
                saga.isPrimaryAccount());
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
