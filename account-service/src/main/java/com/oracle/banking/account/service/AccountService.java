package com.oracle.banking.account.service;

import com.oracle.banking.account.dto.AccountDtos.AccountDetailsResponse;
import com.oracle.banking.account.dto.AccountDtos.AccountSummaryResponse;
import com.oracle.banking.account.dto.AccountDtos.BalanceResponse;
import com.oracle.banking.account.dto.AccountDtos.CreateAccountRequest;
import com.oracle.banking.account.dto.AccountDtos.InternalAccountValidationResponse;
import com.oracle.banking.account.dto.AccountDtos.MiniStatementResponse;
import com.oracle.banking.account.dto.AccountDtos.MoneyMovementRequest;
import com.oracle.banking.account.dto.AccountDtos.TransactionSummaryResponse;
import com.oracle.banking.account.dto.AccountDtos.UpdateAccountStatusRequest;
import com.oracle.banking.account.entity.Account;
import com.oracle.banking.account.entity.AccountStatus;
import com.oracle.banking.account.exception.AccountExceptions.BadRequest;
import com.oracle.banking.account.exception.AccountExceptions.Duplicate;
import com.oracle.banking.account.exception.AccountExceptions.Forbidden;
import com.oracle.banking.account.exception.AccountExceptions.InsufficientBalance;
import com.oracle.banking.account.exception.AccountExceptions.NotFound;
import com.oracle.banking.account.repository.AccountRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.math.BigDecimal;
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

    private final AccountRepository repository;
    private final RestClient transactionClient;
    private final String internalApiKey;

    public AccountService(AccountRepository repository, RestClient.Builder restClientBuilder,
            @Value("${services.transaction-service-url}") String transactionServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.repository = repository;
        this.transactionClient = restClientBuilder.baseUrl(transactionServiceUrl).build();
        this.internalApiKey = internalApiKey;
    }

    public List<AccountSummaryResponse> accountsFor(String username, boolean admin, String customerUsername) {
        if (admin && customerUsername == null) {
            return repository.findAll().stream().map(AccountSummaryResponse::from).toList();
        }
        String owner = admin ? customerUsername : username;
        return repository.findByCustomerUsername(owner).stream().map(AccountSummaryResponse::from).toList();
    }

    public AccountDetailsResponse details(String accountId, String username, boolean admin) {
        Account account = findOwnedOrAdmin(accountId, username, admin);
        return AccountDetailsResponse.from(account);
    }

    public BalanceResponse balance(String accountId, String username, boolean admin) {
        return BalanceResponse.from(findOwnedOrAdmin(accountId, username, admin));
    }

    public MiniStatementResponse miniStatement(String accountId, String username, boolean admin, int limit) {
        Account account = findOwnedOrAdmin(accountId, username, admin);
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
    public AccountDetailsResponse create(CreateAccountRequest request) {
        if (repository.existsByAccountNumber(request.accountNumber())) {
            throw new Duplicate("Account number already exists");
        }
        if (request.initialBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequest("Initial balance cannot be negative");
        }
        Account account = new Account();
        account.setCustomerUsername(request.customerUsername());
        account.setAccountNumber(request.accountNumber());
        account.setAccountType(request.accountType());
        account.setAvailableBalance(request.initialBalance());
        account.setLedgerBalance(request.initialBalance());
        account.setPrimaryAccount(request.primaryAccount());
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedVia("ADMIN");
        Account saved = repository.save(account);
        log.info("Created account {} for customer {}", saved.getAccountId(), saved.getCustomerUsername());
        return AccountDetailsResponse.from(saved);
    }

    @Transactional
    public AccountDetailsResponse updateStatus(String accountId, UpdateAccountStatusRequest request) {
        Account account = find(accountId);
        account.setStatus(request.status());
        log.info("Updated account {} status to {}", accountId, request.status());
        return AccountDetailsResponse.from(repository.save(account));
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
        Account account = find(accountId);
        requirePositive(request.amount());
        requireActive(account);
        account.setAvailableBalance(account.getAvailableBalance().add(request.amount()));
        account.setLedgerBalance(account.getLedgerBalance().add(request.amount()));
        log.info("Credited account {} reference {}", accountId, request.referenceNumber());
        return BalanceResponse.from(repository.save(account));
    }

    @Transactional
    public BalanceResponse debit(String accountId, MoneyMovementRequest request) {
        Account account = find(accountId);
        requirePositive(request.amount());
        requireActive(account);
        if (account.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalance("Insufficient balance");
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.amount()));
        account.setLedgerBalance(account.getLedgerBalance().subtract(request.amount()));
        log.info("Debited account {} reference {}", accountId, request.referenceNumber());
        return BalanceResponse.from(repository.save(account));
    }

    private Account findOwnedOrAdmin(String accountId, String username, boolean admin) {
        Account account = find(accountId);
        if (!admin && !account.getCustomerUsername().equals(username)) {
            throw new Forbidden("Account does not belong to authenticated customer");
        }
        return account;
    }

    private Account find(String accountId) {
        return repository.findById(accountId).orElseThrow(() -> new NotFound("Account not found"));
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
