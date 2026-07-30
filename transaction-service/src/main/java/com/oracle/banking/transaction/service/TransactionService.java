package com.oracle.banking.transaction.service;

import com.oracle.banking.transaction.dto.TransactionDtos.RecordTransactionRequest;
import com.oracle.banking.transaction.dto.TransactionDtos.StatementResponse;
import com.oracle.banking.transaction.dto.TransactionDtos.TransactionResponse;
import com.oracle.banking.transaction.dto.TransactionDtos.TransactionSummaryResponse;
import com.oracle.banking.transaction.entity.BankTransaction;
import com.oracle.banking.transaction.entity.TransactionStatus;
import com.oracle.banking.transaction.entity.TransactionType;
import com.oracle.banking.transaction.exception.TransactionExceptions.BadRequest;
import com.oracle.banking.transaction.exception.TransactionExceptions.Duplicate;
import com.oracle.banking.transaction.exception.TransactionExceptions.Forbidden;
import com.oracle.banking.transaction.exception.TransactionExceptions.NotFound;
import com.oracle.banking.transaction.repository.BankTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final Set<String> SORT_FIELDS = Set.of("transactionDate", "amount", "status", "transactionType", "referenceNumber");

    private final BankTransactionRepository repository;

    public TransactionService(BankTransactionRepository repository) {
        this.repository = repository;
    }

    public Page<TransactionResponse> list(String username, boolean admin, int page, int size) {
        Specification<BankTransaction> spec = admin ? null : byCustomer(username);
        return repository.findAll(spec, pageable(page, size, "transactionDate", "desc")).map(TransactionResponse::from);
    }

    public TransactionResponse byId(String id, String username, boolean admin) {
        BankTransaction transaction = repository.findById(id).orElseThrow(() -> new NotFound("Transaction not found"));
        requireOwnerOrAdmin(transaction, username, admin);
        return TransactionResponse.from(transaction);
    }

    public Page<TransactionResponse> byAccount(String accountId, String username, boolean admin, int page, int size) {
        Specification<BankTransaction> spec = byAccount(accountId).and(admin ? null : byCustomer(username));
        return repository.findAll(spec, pageable(page, size, "transactionDate", "desc")).map(TransactionResponse::from);
    }

    public Page<TransactionResponse> search(
            String username,
            boolean admin,
            String accountId,
            String accountNumber,
            TransactionType transactionType,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String referenceNumber,
            Instant fromDate,
            Instant toDate,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Specification<BankTransaction> spec = searchSpec(admin ? null : username, accountId, accountNumber, transactionType,
                status, minAmount, maxAmount, referenceNumber, fromDate, toDate);
        return repository.findAll(spec, pageable(page, size, sortBy, direction)).map(TransactionResponse::from);
    }

    public StatementResponse statement(String username, boolean admin, String accountId, Instant fromDate, Instant toDate) {
        if (accountId == null || accountId.isBlank()) {
            throw new BadRequest("accountId is required");
        }
        Specification<BankTransaction> spec = searchSpec(admin ? null : username, accountId, null, null, null, null, null, null, fromDate, toDate);
        List<TransactionResponse> transactions = repository.findAll(spec, Sort.by(Sort.Direction.DESC, "transactionDate"))
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return new StatementResponse(accountId, fromDate, toDate, transactions);
    }

    public List<TransactionSummaryResponse> recentForAccount(String accountId, int limit) {
        return repository.findAll(byAccount(accountId), pageable(0, Math.min(Math.max(limit, 1), 25), "transactionDate", "desc"))
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }

    @Transactional
    public TransactionResponse record(RecordTransactionRequest request) {
        if (request.referenceNumber() != null) {
            BankTransaction existing = repository.findByReferenceNumber(request.referenceNumber()).orElse(null);
            if (existing != null) {
                if (!existing.getAccountId().equals(request.accountId())
                        || existing.getAmount().compareTo(request.amount()) != 0
                        || existing.getDebitCredit() != request.debitCredit()) {
                    throw new Duplicate("Transaction reference does not match the original request");
                }
                return TransactionResponse.from(existing);
            }
        }
        BankTransaction transaction = new BankTransaction();
        transaction.setAccountId(request.accountId());
        transaction.setAccountNumber(request.accountNumber());
        transaction.setCustomerUsername(request.customerUsername());
        transaction.setTransactionType(request.transactionType());
        transaction.setReferenceNumber(request.referenceNumber());
        transaction.setReferenceType(request.referenceType());
        transaction.setAmount(request.amount());
        transaction.setDebitCredit(request.debitCredit());
        transaction.setStatus(request.status() == null ? TransactionStatus.SUCCESS : request.status());
        transaction.setDescription(request.description());
        transaction.setTransactionDate(request.transactionDate());
        BankTransaction saved = repository.save(transaction);
        log.info("Recorded transaction {} for account {}", saved.getTransactionId(), saved.getAccountId());
        return TransactionResponse.from(saved);
    }

    @Transactional
    public TransactionResponse reverse(String referenceNumber) {
        BankTransaction transaction = repository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new NotFound("Transaction not found"));
        if (transaction.getStatus() != TransactionStatus.REVERSED) {
            transaction.setStatus(TransactionStatus.REVERSED);
            log.info("Reversed transaction {} reference {}", transaction.getTransactionId(), referenceNumber);
        }
        return TransactionResponse.from(repository.save(transaction));
    }

    private Specification<BankTransaction> searchSpec(
            String customerUsername,
            String accountId,
            String accountNumber,
            TransactionType transactionType,
            TransactionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String referenceNumber,
            Instant fromDate,
            Instant toDate
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerUsername != null) predicates.add(builder.equal(root.get("customerUsername"), customerUsername));
            if (accountId != null && !accountId.isBlank()) predicates.add(builder.equal(root.get("accountId"), accountId));
            if (accountNumber != null && !accountNumber.isBlank()) predicates.add(builder.equal(root.get("accountNumber"), accountNumber));
            if (transactionType != null) predicates.add(builder.equal(root.get("transactionType"), transactionType));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (minAmount != null) predicates.add(builder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            if (maxAmount != null) predicates.add(builder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            if (referenceNumber != null && !referenceNumber.isBlank()) predicates.add(builder.equal(root.get("referenceNumber"), referenceNumber));
            if (fromDate != null) predicates.add(builder.greaterThanOrEqualTo(root.get("transactionDate"), fromDate));
            if (toDate != null) predicates.add(builder.lessThanOrEqualTo(root.get("transactionDate"), toDate));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<BankTransaction> byCustomer(String username) {
        return (root, query, builder) -> builder.equal(root.get("customerUsername"), username);
    }

    private Specification<BankTransaction> byAccount(String accountId) {
        return (root, query, builder) -> builder.equal(root.get("accountId"), accountId);
    }

    private Pageable pageable(int page, int size, String sortBy, String direction) {
        String field = SORT_FIELDS.contains(sortBy) ? sortBy : "transactionDate";
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(sortDirection, field));
    }

    private void requireOwnerOrAdmin(BankTransaction transaction, String username, boolean admin) {
        if (!admin && !transaction.getCustomerUsername().equals(username)) {
            throw new Forbidden("Transaction does not belong to authenticated customer");
        }
    }
}
