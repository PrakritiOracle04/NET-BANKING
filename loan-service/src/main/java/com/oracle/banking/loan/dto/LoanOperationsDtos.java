package com.oracle.banking.loan.dto;

import com.oracle.banking.loan.entity.Loan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

public final class LoanOperationsDtos {
    private LoanOperationsDtos() {}

    public record LoanItem(
            String loanId, String customerUserId, String linkedAccountId, String maskedLoanNumber,
            String loanType, BigDecimal principalAmount, BigDecimal outstandingBalance,
            BigDecimal emiAmount, String status, LocalDate maturityDate, Instant createdAt) {
        public static LoanItem from(Loan loan) {
            String number = loan.getLoanNumber();
            String masked = number == null || number.length() <= 4
                    ? "****"
                    : "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
            return new LoanItem(
                    loan.getLoanId(), loan.getCustomerUserId(), loan.getLinkedAccountId(), masked,
                    loan.getLoanType().name(), loan.getPrincipalAmount(), loan.getOutstandingBalance(),
                    loan.getEmiAmount(), loan.getStatus().name(), loan.getMaturityDate(), loan.getCreatedAt());
        }
    }

    public record LoanPage(List<LoanItem> items, int page, int size, long totalElements, int totalPages) {
        public static LoanPage from(Page<Loan> result) {
            return new LoanPage(result.getContent().stream().map(LoanItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record LoanSummary(long total, long active, long overdue, long defaulted, long closed) {}
}
