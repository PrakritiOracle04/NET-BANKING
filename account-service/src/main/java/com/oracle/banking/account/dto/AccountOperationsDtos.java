package com.oracle.banking.account.dto;

import com.oracle.banking.account.entity.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class AccountOperationsDtos {
    private AccountOperationsDtos() {}

    public record AccountItem(
            String accountId,
            String customerUserId,
            String maskedAccountNumber,
            String accountType,
            String branchIfsc,
            String status,
            BigDecimal availableBalance,
            BigDecimal ledgerBalance,
            boolean primaryAccount,
            Instant createdAt,
            Instant updatedAt) {
        public static AccountItem from(Account account) {
            String number = account.getAccountNumber();
            String masked = number == null || number.length() <= 4
                    ? "****"
                    : "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
            return new AccountItem(
                    account.getAccountId(), account.getCustomerUserId(), masked,
                    account.getAccountType().name(), account.getBranchIfsc(), account.getStatus().name(),
                    account.getAvailableBalance(), account.getLedgerBalance(), account.isPrimaryAccount(),
                    account.getCreatedAt(), account.getUpdatedAt());
        }
    }

    public record AccountPage(List<AccountItem> items, int page, int size, long totalElements, int totalPages) {
        public static AccountPage from(Page<Account> result) {
            return new AccountPage(result.getContent().stream().map(AccountItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record AccountSummary(long total, long active, long frozen, long closed) {}
}
