package com.oracle.banking.transaction.dto;

import com.oracle.banking.transaction.dto.TransactionDtos.TransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class TransactionOperationsDtos {
    private TransactionOperationsDtos() {}

    public record TransactionItem(
            String transactionId,
            String accountId,
            String maskedAccountNumber,
            String customerUserId,
            String transactionType,
            String referenceNumber,
            String referenceType,
            BigDecimal amount,
            String status,
            String debitCredit,
            String description,
            Instant transactionDate) {
        public static TransactionItem from(TransactionResponse transaction) {
            String number = transaction.accountNumber();
            String masked = number == null || number.length() <= 4
                    ? "****"
                    : "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
            return new TransactionItem(
                    transaction.transactionId(), transaction.accountId(), masked, transaction.customerUserId(),
                    transaction.transactionType().name(), transaction.referenceNumber(), transaction.referenceType(),
                    transaction.amount(), transaction.status().name(), transaction.debitCredit().name(),
                    transaction.description(), transaction.transactionDate());
        }
    }

    public record TransactionPage(List<TransactionItem> items, int page, int size, long totalElements, int totalPages) {
        public static TransactionPage from(Page<TransactionResponse> result) {
            return new TransactionPage(result.getContent().stream().map(TransactionItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record TransactionSummary(long total, long successful, long failed, long reversed) {}
}
