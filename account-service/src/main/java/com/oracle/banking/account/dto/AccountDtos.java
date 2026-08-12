package com.oracle.banking.account.dto;

import com.oracle.banking.account.entity.Account;
import com.oracle.banking.account.entity.AccountStatus;
import com.oracle.banking.account.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {
    private AccountDtos() {}

    public record InternalOpenAccountRequest(
            @NotBlank @Size(max = 36) String customerUserId,
            @NotNull AccountType accountType,
            @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC") String branchIfsc,
            @NotBlank @Size(max = 80) String openingReference,
            @NotNull @DecimalMin(value = "0.01") BigDecimal initialDeposit
    ) {}

    public record UpdateAccountStatusRequest(@NotNull AccountStatus status) {}

    public record MoneyMovementRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 80) String referenceNumber,
            @Size(max = 160) String description
    ) {}

    public record AccountSummaryResponse(
            String accountId,
            String accountNumber,
            AccountType accountType,
            String branchIfsc,
            AccountStatus status,
            BigDecimal availableBalance,
            boolean primaryAccount
    ) {
        public static AccountSummaryResponse from(Account account) {
            return new AccountSummaryResponse(
                    account.getAccountId(),
                    account.getAccountNumber(),
                    account.getAccountType(),
                    account.getBranchIfsc(),
                    account.getStatus(),
                    account.getAvailableBalance(),
                    account.isPrimaryAccount()
            );
        }
    }

    public record AccountDetailsResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            AccountType accountType,
            String branchIfsc,
            AccountStatus status,
            BigDecimal availableBalance,
            BigDecimal ledgerBalance,
            boolean primaryAccount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AccountDetailsResponse from(Account account) {
            return new AccountDetailsResponse(
                    account.getAccountId(),
                    account.getCustomerUserId(),
                    account.getAccountNumber(),
                    account.getAccountType(),
                    account.getBranchIfsc(),
                    account.getStatus(),
                    account.getAvailableBalance(),
                    account.getLedgerBalance(),
                    account.isPrimaryAccount(),
                    account.getCreatedAt(),
                    account.getUpdatedAt()
            );
        }
    }

    public record BalanceResponse(String accountId, String accountNumber, BigDecimal availableBalance, BigDecimal ledgerBalance) {
        public static BalanceResponse from(Account account) {
            return new BalanceResponse(account.getAccountId(), account.getAccountNumber(), account.getAvailableBalance(), account.getLedgerBalance());
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
    ) {}

    public record MiniStatementResponse(String accountId, String accountNumber, List<TransactionSummaryResponse> transactions) {}

    public record InternalAccountValidationResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            String branchIfsc,
            AccountStatus status,
            BigDecimal availableBalance,
            boolean active
    ) {
        public static InternalAccountValidationResponse from(Account account) {
            return new InternalAccountValidationResponse(
                    account.getAccountId(),
                    account.getCustomerUserId(),
                    account.getAccountNumber(),
                    account.getBranchIfsc(),
                    account.getStatus(),
                    account.getAvailableBalance(),
                    account.getStatus() == AccountStatus.ACTIVE
            );
        }
    }

    public record MiniStatementQuery(@Min(1) @Max(25) int limit) {}
}
