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
import com.oracle.banking.workflow.exception.WorkflowExceptions.BadRequest;
import com.oracle.banking.workflow.exception.WorkflowExceptions.DownstreamFailure;
import com.oracle.banking.workflow.exception.WorkflowExceptions.Forbidden;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final String internalApiKey;

    public BankingWorkflowService(RestClient.Builder restClientBuilder,
            WorkflowEventPublisher events,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.beneficiary-service-url}") String beneficiaryServiceUrl,
            @Value("${services.transaction-service-url}") String transactionServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.accountClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.beneficiaryClient = restClientBuilder.baseUrl(beneficiaryServiceUrl).build();
        this.transactionClient = restClientBuilder.baseUrl(transactionServiceUrl).build();
        this.events = events;
        this.internalApiKey = internalApiKey;
    }

    public DepositResponse deposit(String username, boolean admin, DepositRequest request) {
        String reference = reference("DEP");
        InternalAccountValidationResponse account = validateAccount(request.accountId());
        requireOwnerOrAdmin(account, username, admin);
        requireActive(account);

        credit(account.accountId(), request.amount(), reference, request.description());
        TransactionResponse transaction = record(account, "DEPOSIT", reference, "DEPOSIT", request.amount(), "CREDIT", request.description());

        events.accountCredited(event("account-credited", reference, account.accountId(), request.amount()));
        events.transactionCreated(event("transaction-created", reference, account.accountId(), request.amount()));
        log.info("Deposit workflow completed for account {} reference {}", account.accountId(), reference);
        return new DepositResponse(reference, account.accountId(), request.amount(), "SUCCESS", transaction.transactionId());
    }

    public WithdrawResponse withdraw(String username, boolean admin, WithdrawRequest request) {
        String reference = reference("WDR");
        InternalAccountValidationResponse account = validateAccount(request.accountId());
        requireOwnerOrAdmin(account, username, admin);
        requireActive(account);
        requireSufficientBalance(account, request.amount());

        debit(account.accountId(), request.amount(), reference, request.description());
        TransactionResponse transaction = record(account, "WITHDRAWAL", reference, "WITHDRAWAL", request.amount(), "DEBIT", request.description());

        events.accountDebited(event("account-debited", reference, account.accountId(), request.amount()));
        events.transactionCreated(event("transaction-created", reference, account.accountId(), request.amount()));
        log.info("Withdraw workflow completed for account {} reference {}", account.accountId(), reference);
        return new WithdrawResponse(reference, account.accountId(), request.amount(), "SUCCESS", transaction.transactionId());
    }

    public TransferResponse transfer(String username, boolean admin, TransferRequest request) {
        String reference = reference("TRF");
        InternalAccountValidationResponse source = validateAccount(request.sourceAccountId());
        InternalAccountValidationResponse destination = validateAccountByNumber(request.destinationAccountNumber());
        requireOwnerOrAdmin(source, username, admin);
        requireActive(source);
        requireActive(destination);
        requireDifferentAccounts(source, destination);
        requireSufficientBalance(source, request.amount());
        verifyBeneficiary(username, destination.accountNumber());

        debit(source.accountId(), request.amount(), reference, request.description());
        credit(destination.accountId(), request.amount(), reference, request.description());

        TransactionResponse debitTransaction = record(source, "TRANSFER", reference, "TRANSFER", request.amount(), "DEBIT", request.description());
        TransactionResponse creditTransaction = record(destination, "TRANSFER", reference + "-CR", "TRANSFER", request.amount(), "CREDIT", request.description());

        events.accountDebited(event("account-debited", reference, source.accountId(), request.amount()));
        events.accountCredited(event("account-credited", reference, destination.accountId(), request.amount()));
        events.transactionCreated(event("transaction-created", reference, source.accountId(), request.amount()));
        log.info("Transfer workflow completed from {} to {} reference {}", source.accountId(), destination.accountId(), reference);
        return new TransferResponse(reference, source.accountId(), destination.accountId(), request.amount(), "SUCCESS",
                debitTransaction.transactionId(), creditTransaction.transactionId());
    }

    private InternalAccountValidationResponse validateAccount(String accountId) {
        try {
            return accountClient.get()
                    .uri("/internal/accounts/{id}/validate", accountId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalAccountValidationResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Account validation failed");
        }
    }

    private InternalAccountValidationResponse validateAccountByNumber(String accountNumber) {
        try {
            return accountClient.get()
                    .uri("/internal/accounts/number/{accountNumber}/validate", accountNumber)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(InternalAccountValidationResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Destination account validation failed");
        }
    }

    private void verifyBeneficiary(String customerUsername, String destinationAccountNumber) {
        try {
            BeneficiaryVerificationResponse response = beneficiaryClient.post()
                    .uri("/internal/beneficiaries/verify-transfer")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new BeneficiaryVerificationRequest(customerUsername, destinationAccountNumber))
                    .retrieve()
                    .body(BeneficiaryVerificationResponse.class);
            if (response == null || !response.verified()) {
                throw new BadRequest("Beneficiary is not verified");
            }
          } catch (BadRequest ex) {
              throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BadRequest("Beneficiary is not verified");
            }
            throw new DownstreamFailure("Beneficiary verification failed");
          } catch (RestClientException ex) {
              throw new DownstreamFailure("Beneficiary verification failed");
          }
    }

    private void credit(String accountId, java.math.BigDecimal amount, String reference, String description) {
        moneyMovement(accountId, "/internal/accounts/{id}/credit", amount, reference, description);
    }

    private void debit(String accountId, java.math.BigDecimal amount, String reference, String description) {
        moneyMovement(accountId, "/internal/accounts/{id}/debit", amount, reference, description);
    }

    private void moneyMovement(String accountId, String uri, java.math.BigDecimal amount, String reference, String description) {
        try {
            accountClient.post()
                    .uri(uri, accountId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new MoneyMovementRequest(amount, reference, description))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Account balance update failed");
        }
    }

    private TransactionResponse record(InternalAccountValidationResponse account, String type, String reference, String referenceType,
            java.math.BigDecimal amount, String debitCredit, String description) {
        try {
            return transactionClient.post()
                    .uri("/internal/transactions")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .body(new RecordTransactionRequest(account.accountId(), account.accountNumber(), account.customerUsername(),
                            type, reference, referenceType, amount, debitCredit, "SUCCESS", description, Instant.now()))
                    .retrieve()
                    .body(TransactionResponse.class);
        } catch (RestClientException ex) {
            throw new DownstreamFailure("Transaction record creation failed");
        }
    }

    private void requireOwnerOrAdmin(InternalAccountValidationResponse account, String username, boolean admin) {
        if (!admin && !account.customerUsername().equals(username)) {
            throw new Forbidden("Account does not belong to authenticated customer");
        }
    }

    private void requireActive(InternalAccountValidationResponse account) {
        if (!account.active()) {
            throw new BadRequest("Only active accounts can be used for banking operations");
        }
    }

    private void requireSufficientBalance(InternalAccountValidationResponse account, java.math.BigDecimal amount) {
        if (account.availableBalance().compareTo(amount) < 0) {
            throw new BadRequest("Insufficient balance");
        }
    }

    private void requireDifferentAccounts(InternalAccountValidationResponse source, InternalAccountValidationResponse destination) {
        if (source.accountId().equals(destination.accountId())) {
            throw new BadRequest("Source and destination accounts must be different");
        }
    }

    private DomainEvent event(String eventType, String reference, String accountId, java.math.BigDecimal amount) {
        return new DomainEvent(eventType, reference, accountId, amount, "SUCCESS", Instant.now());
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
