package com.oracle.banking.transaction.dto;

import com.oracle.banking.transaction.entity.BankTransaction;
import com.oracle.banking.transaction.entity.DebitCredit;
import com.oracle.banking.transaction.entity.TransactionStatus;
import com.oracle.banking.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TransactionDtos {
    private TransactionDtos() {}

    public record RecordTransactionRequest(
            @NotBlank @Size(max = 36) String accountId,
            @NotBlank @Size(max = 30) String accountNumber,
            @NotBlank @Size(max = 36) String customerUserId,
            @NotNull TransactionType transactionType,
            @Size(max = 80) String referenceNumber,
            @Size(max = 40) String referenceType,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull DebitCredit debitCredit,
            TransactionStatus status,
            @Size(max = 240) String description,
            Instant transactionDate
    ) {}

    public record TransactionResponse(
            String transactionId,
            String accountId,
            String accountNumber,
            String customerUserId,
            TransactionType transactionType,
            String referenceNumber,
            String referenceType,
            BigDecimal amount,
            TransactionStatus status,
            DebitCredit debitCredit,
            String description,
            Instant transactionDate
    ) {
        public static TransactionResponse from(BankTransaction transaction) {
            return new TransactionResponse(
                    transaction.getTransactionId(),
                    transaction.getAccountId(),
                    transaction.getAccountNumber(),
                    transaction.getCustomerUserId(),
                    transaction.getTransactionType(),
                    transaction.getReferenceNumber(),
                    transaction.getReferenceType(),
                    transaction.getAmount(),
                    transaction.getStatus(),
                    transaction.getDebitCredit(),
                    transaction.getDescription(),
                    transaction.getTransactionDate()
            );
        }
    }

    public record TransactionSummaryResponse(
            String transactionId,
            String transactionType,
            String referenceNumber,
            BigDecimal amount,
            String debitCredit,
            String status,
            Instant transactionDate
    ) {
        public static TransactionSummaryResponse from(BankTransaction transaction) {
            return new TransactionSummaryResponse(
                    transaction.getTransactionId(),
                    transaction.getTransactionType().name(),
                    transaction.getReferenceNumber(),
                    transaction.getAmount(),
                    transaction.getDebitCredit().name(),
                    transaction.getStatus().name(),
                    transaction.getTransactionDate()
            );
        }
    }

    public record StatementResponse(
            String accountId,
            Instant fromDate,
            Instant toDate,
            List<TransactionResponse> transactions
    ) {}
}
