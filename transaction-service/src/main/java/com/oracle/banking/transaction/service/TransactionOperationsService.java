package com.oracle.banking.transaction.service;

import com.oracle.banking.transaction.dto.TransactionOperationsDtos.TransactionPage;
import com.oracle.banking.transaction.dto.TransactionOperationsDtos.TransactionSummary;
import com.oracle.banking.transaction.entity.TransactionStatus;
import com.oracle.banking.transaction.entity.TransactionType;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TransactionOperationsService {
    private final TransactionService transactions;

    public TransactionOperationsService(TransactionService transactions) {
        this.transactions = transactions;
    }

    public TransactionPage search(
            String customerUserId, String accountId, TransactionType type, TransactionStatus status,
            Instant fromDate, Instant toDate, int page, int size) {
        return TransactionPage.from(transactions.operationsSearch(
                customerUserId, accountId, type, status, fromDate, toDate, page, size));
    }

    public TransactionSummary summary() {
        return new TransactionSummary(
                transactions.count(),
                transactions.countByStatus(TransactionStatus.SUCCESS),
                transactions.countByStatus(TransactionStatus.FAILED),
                transactions.countByStatus(TransactionStatus.REVERSED));
    }
}
