package com.oracle.banking.account.service;

import com.oracle.banking.account.dto.AccountDtos.AccountDetailsResponse;
import com.oracle.banking.account.dto.AccountDtos.AccountSummaryResponse;
import com.oracle.banking.account.dto.AccountDtos.BalanceResponse;
import com.oracle.banking.account.dto.AccountDtos.InternalAccountValidationResponse;
import com.oracle.banking.account.dto.AccountDtos.InternalOpenAccountRequest;
import com.oracle.banking.account.dto.AccountDtos.MiniStatementResponse;
import com.oracle.banking.account.dto.AccountDtos.MoneyMovementRequest;
import com.oracle.banking.account.dto.AccountDtos.TransactionSummaryResponse;
import com.oracle.banking.account.dto.AccountDtos.UpdateAccountStatusRequest;
import com.oracle.banking.account.entity.Account;
import com.oracle.banking.account.entity.AccountMovement;
import com.oracle.banking.account.entity.AccountStatus;
import com.oracle.banking.account.entity.BalanceOperation;
import com.oracle.banking.account.exception.AccountExceptions.BadRequest;
import com.oracle.banking.account.exception.AccountExceptions.Duplicate;
import com.oracle.banking.account.exception.AccountExceptions.Forbidden;
import com.oracle.banking.account.exception.AccountExceptions.InsufficientBalance;
import com.oracle.banking.account.exception.AccountExceptions.NotFound;
import com.oracle.banking.account.repository.AccountRepository;
import com.oracle.banking.account.repository.AccountMovementRepository;
import com.oracle.banking.account.event.AccountAuditPublisher;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom ACCOUNT_NUMBER_RANDOM = new SecureRandom();

    private final AccountRepository repository;
    private final AccountMovementRepository movements;
    private final RestClient transactionClient;
    private final String internalApiKey;
    private final AccountAuditPublisher auditEvents;

    public AccountService(AccountRepository repository, AccountMovementRepository movements, RestClient.Builder restClientBuilder,
            @Value("${services.transaction-service-url}") String transactionServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            AccountAuditPublisher auditEvents) {
        this.repository = repository;
        this.movements = movements;
        this.transactionClient = restClientBuilder.baseUrl(transactionServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.auditEvents = auditEvents;
    }

    public List<AccountSummaryResponse> accountsFor(String userId, boolean admin, String customerUserId) {
        if (admin && customerUserId == null) {
            return repository.findAll().stream().map(AccountSummaryResponse::from).toList();
        }
        String ownerUserId = admin ? customerUserId : userId;
        return repository.findByCustomerUserId(ownerUserId).stream().map(AccountSummaryResponse::from).toList();
    }

    public AccountDetailsResponse details(String accountId, String userId, boolean admin) {
        Account account = findOwnedOrAdmin(accountId, userId, admin);
        return AccountDetailsResponse.from(account);
    }

    public BalanceResponse balance(String accountId, String userId, boolean admin) {
        return BalanceResponse.from(findOwnedOrAdmin(accountId, userId, admin));
    }

    public MiniStatementResponse miniStatement(String accountId, String userId, boolean admin, int limit) {
        Account account = findOwnedOrAdmin(accountId, userId, admin);
        List<TransactionSummaryResponse> transactions;
        try {
            transactions = transactionClient.get()
                    .uri(uri -> uri.path("/internal/transactions/accounts/{accountId}/recent")
                            .queryParam("limit", limit)
                            .build(accountId))
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TransactionSummaryResponse>>() {});
        } catch (RestClientException ex) {
            log.warn("Unable to fetch mini statement for account {}", accountId);
            transactions = List.of();
        }
        return new MiniStatementResponse(account.getAccountId(), account.getAccountNumber(), transactions == null ? List.of() : transactions);
    }

    @Transactional
    public AccountDetailsResponse open(InternalOpenAccountRequest request) {
        Account existing = repository.findByOpeningReference(request.openingReference()).orElse(null);
        if (existing != null) {
            if (!existing.getCustomerUserId().equals(request.customerUserId())
                    || existing.getAccountType() != request.accountType()
                    || !existing.getBranchIfsc().equals(request.branchIfsc())
                    || existing.getInitialDeposit().compareTo(request.initialDeposit()) != 0) {
                throw new Duplicate("Opening reference was already used for another account request");
            }
            return AccountDetailsResponse.from(existing);
        }
        Account account = new Account();
        account.setCustomerUserId(request.customerUserId());
        account.setAccountNumber(generateAccountNumber());
        account.setAccountType(request.accountType());
        account.setBranchIfsc(request.branchIfsc());
        account.setOpeningReference(request.openingReference());
        account.setInitialDeposit(request.initialDeposit());
        account.setAvailableBalance(request.initialDeposit());
        account.setLedgerBalance(request.initialDeposit());
        account.setPrimaryAccount(repository.countByCustomerUserId(request.customerUserId()) == 0);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedVia("WORKFLOW");
        Account saved = repository.save(account);
        log.info("Created account {} for customer user ID {}", saved.getAccountId(), saved.getCustomerUserId());
        return AccountDetailsResponse.from(saved);
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            long suffix = 100_000_000_000L
                    + Math.floorMod(ACCOUNT_NUMBER_RANDOM.nextLong(), 900_000_000_000L);
            String accountNumber = Long.toString(suffix);
            if (!repository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account number");
    }

    @Transactional
    public AccountDetailsResponse updateStatus(String accountId, UpdateAccountStatusRequest request) {
        Account account = find(accountId);
        account.setStatus(request.status());
        log.info("Updated account {} status to {}", accountId, request.status());
        Account saved = repository.save(account);
        auditEvents.statusChanged(saved);
        return AccountDetailsResponse.from(saved);
    }

    public InternalAccountValidationResponse validate(String accountId) {
        return InternalAccountValidationResponse.from(find(accountId));
    }

    public InternalAccountValidationResponse validateByAccountNumber(String accountNumber) {
        return InternalAccountValidationResponse.from(repository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NotFound("Account not found")));
    }

    @Transactional
    public BalanceResponse credit(String accountId, MoneyMovementRequest request) {
        return applyMovement(accountId, request, BalanceOperation.CREDIT);
    }

    @Transactional
    public BalanceResponse debit(String accountId, MoneyMovementRequest request) {
        return applyMovement(accountId, request, BalanceOperation.DEBIT);
    }

    @Transactional
    public BalanceResponse reverseMovement(String accountId, String referenceNumber) {
        Account account = findLocked(accountId);
        AccountMovement original = movements.findLockedByAccountIdAndReferenceNumber(accountId, referenceNumber)
                .orElseThrow(() -> new NotFound("Account movement not found"));
        if (original.isReversed()) {
            return BalanceResponse.from(account);
        }
        if (original.getOperation() == BalanceOperation.CREDIT) {
            if (account.getAvailableBalance().compareTo(original.getAmount()) < 0) {
                throw new InsufficientBalance("Insufficient balance to reverse credit");
            }
            account.setAvailableBalance(account.getAvailableBalance().subtract(original.getAmount()));
            account.setLedgerBalance(account.getLedgerBalance().subtract(original.getAmount()));
        } else {
            account.setAvailableBalance(account.getAvailableBalance().add(original.getAmount()));
            account.setLedgerBalance(account.getLedgerBalance().add(original.getAmount()));
        }
        String reversalReference = referenceNumber + ":REVERSAL";
        original.markReversed(reversalReference);
        movements.save(new AccountMovement(accountId, reversalReference,
                original.getOperation() == BalanceOperation.CREDIT ? BalanceOperation.DEBIT : BalanceOperation.CREDIT,
                original.getAmount(), "Saga compensation for " + referenceNumber));
        log.info("Reversed account movement {} for account {}", referenceNumber, accountId);
        return BalanceResponse.from(repository.save(account));
    }

    private BalanceResponse applyMovement(String accountId, MoneyMovementRequest request, BalanceOperation operation) {
        requirePositive(request.amount());
        if (request.referenceNumber() == null || request.referenceNumber().isBlank()) {
            throw new BadRequest("referenceNumber is required");
        }
        Account account = findLocked(accountId);
        AccountMovement existing = movements.findLockedByAccountIdAndReferenceNumber(accountId, request.referenceNumber()).orElse(null);
        if (existing != null) {
            if (existing.getOperation() != operation || existing.getAmount().compareTo(request.amount()) != 0) {
                throw new BadRequest("Movement reference does not match the original request");
            }
            return BalanceResponse.from(find(accountId));
        }
        requirePositive(request.amount());
        requireActive(account);
        if (operation == BalanceOperation.DEBIT && account.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalance("Insufficient balance");
        }
        if (operation == BalanceOperation.CREDIT) {
            account.setAvailableBalance(account.getAvailableBalance().add(request.amount()));
            account.setLedgerBalance(account.getLedgerBalance().add(request.amount()));
        } else {
            account.setAvailableBalance(account.getAvailableBalance().subtract(request.amount()));
            account.setLedgerBalance(account.getLedgerBalance().subtract(request.amount()));
        }
        movements.save(new AccountMovement(accountId, request.referenceNumber(), operation, request.amount(), request.description()));
        log.info("{} account {} reference {}", operation, accountId, request.referenceNumber());
        return BalanceResponse.from(repository.save(account));
    }

    private Account findOwnedOrAdmin(String accountId, String userId, boolean admin) {
        Account account = find(accountId);
        if (!admin && !account.getCustomerUserId().equals(userId)) {
            throw new Forbidden("Account does not belong to authenticated customer");
        }
        return account;
    }

    private Account find(String accountId) {
        return repository.findById(accountId).orElseThrow(() -> new NotFound("Account not found"));
    }

    private Account findLocked(String accountId) {
        return repository.findLockedByAccountId(accountId).orElseThrow(() -> new NotFound("Account not found"));
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequest("Amount must be greater than zero");
        }
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequest("Only active accounts can be used for banking operations");
        }
    }
}
