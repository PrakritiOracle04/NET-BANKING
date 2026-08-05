package com.oracle.banking.loan.dto;

import com.oracle.banking.loan.entity.EmiSchedule;
import com.oracle.banking.loan.entity.EmiStatus;
import com.oracle.banking.loan.entity.EmploymentType;
import com.oracle.banking.loan.entity.Loan;
import com.oracle.banking.loan.entity.LoanApplication;
import com.oracle.banking.loan.entity.LoanApplicationStatus;
import com.oracle.banking.loan.entity.LoanRepayment;
import com.oracle.banking.loan.entity.LoanRepaymentStatus;
import com.oracle.banking.loan.entity.LoanStatus;
import com.oracle.banking.loan.entity.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LoanDtos {
    private LoanDtos() {}

    public record RegisterLoanRequest(
            @NotBlank @Size(max = 36) String customerUserId,
            @NotBlank @Size(max = 36) String linkedAccountId,
            @NotNull LoanType loanType,
            @NotNull @DecimalMin(value = "0.01") BigDecimal principalAmount,
            @NotNull @DecimalMin(value = "0.00") BigDecimal annualInterestRate,
            @Min(1) @Max(360) int tenureMonths,
            LocalDate startDate) {}

    public record LoanTypeOption(String code, String label) {}

    public record LoanApplicationRequest(
            @NotBlank @Size(max = 36) String linkedAccountId,
            @NotNull LoanType loanType,
            @NotNull @DecimalMin(value = "0.01") BigDecimal requestedAmount,
            @Min(1) @Max(360) int tenureMonths,
            @NotNull @DecimalMin(value = "0.00") BigDecimal monthlyIncome,
            @NotNull EmploymentType employmentType,
            @NotBlank @Size(max = 500) String purpose) {}

    public record LoanApplicationApprovalRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal approvedAmount,
            @NotNull @DecimalMin(value = "0.00") BigDecimal annualInterestRate,
            @Min(1) @Max(360) int tenureMonths,
            LocalDate startDate,
            @Size(max = 500) String notes) {}

    public record LoanApplicationRejectionRequest(@NotBlank @Size(max = 500) String reason) {}

    public record LoanApplicationResponse(
            String applicationId,
            String customerUserId,
            String linkedAccountId,
            LoanType loanType,
            BigDecimal requestedAmount,
            int requestedTenureMonths,
            BigDecimal monthlyIncome,
            EmploymentType employmentType,
            String purpose,
            BigDecimal approvedAmount,
            BigDecimal approvedAnnualInterestRate,
            Integer approvedTenureMonths,
            LoanApplicationStatus status,
            String rejectionReason,
            String decisionNotes,
            String issuedLoanId,
            String decidedByUserId,
            Instant createdAt,
            Instant updatedAt,
            Instant decidedAt) {
        public static LoanApplicationResponse from(LoanApplication application) {
            return new LoanApplicationResponse(
                    application.getApplicationId(), application.getCustomerUserId(), application.getLinkedAccountId(),
                    application.getLoanType(), application.getRequestedAmount(), application.getRequestedTenureMonths(),
                    application.getMonthlyIncome(), application.getEmploymentType(), application.getPurpose(),
                    application.getApprovedAmount(), application.getApprovedAnnualInterestRate(),
                    application.getApprovedTenureMonths(), application.getStatus(), application.getRejectionReason(),
                    application.getDecisionNotes(), application.getIssuedLoanId(), application.getDecidedByUserId(),
                    application.getCreatedAt(), application.getUpdatedAt(), application.getDecidedAt());
        }
    }

    public record UpdateLoanStatusRequest(@NotNull LoanStatus status) {}

    public record CalculateEmiRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal loanAmount,
            @NotNull @DecimalMin(value = "0.00") BigDecimal annualInterestRate,
            @Min(1) @Max(360) int tenureMonths,
            LocalDate startDate) {}

    public record LoanSummaryResponse(
            String loanId,
            String customerUserId,
            String linkedAccountId,
            String loanNumber,
            LoanType loanType,
            BigDecimal principalAmount,
            BigDecimal emiAmount,
            BigDecimal outstandingBalance,
            LoanStatus status,
            LocalDate startDate,
            LocalDate maturityDate) {
        public static LoanSummaryResponse from(Loan loan) {
            return new LoanSummaryResponse(
                    loan.getLoanId(),
                    loan.getCustomerUserId(),
                    loan.getLinkedAccountId(),
                    loan.getLoanNumber(),
                    loan.getLoanType(),
                    loan.getPrincipalAmount(),
                    loan.getEmiAmount(),
                    loan.getOutstandingBalance(),
                    loan.getStatus(),
                    loan.getStartDate(),
                    loan.getMaturityDate());
        }
    }

    public record LoanDetailsResponse(
            String loanId,
            String customerUserId,
            String linkedAccountId,
            String loanNumber,
            LoanType loanType,
            BigDecimal principalAmount,
            BigDecimal annualInterestRate,
            Integer tenureMonths,
            BigDecimal emiAmount,
            BigDecimal outstandingBalance,
            LocalDate startDate,
            LocalDate maturityDate,
            LoanStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt) {
        public static LoanDetailsResponse from(Loan loan) {
            return new LoanDetailsResponse(
                    loan.getLoanId(),
                    loan.getCustomerUserId(),
                    loan.getLinkedAccountId(),
                    loan.getLoanNumber(),
                    loan.getLoanType(),
                    loan.getPrincipalAmount(),
                    loan.getAnnualInterestRate(),
                    loan.getTenureMonths(),
                    loan.getEmiAmount(),
                    loan.getOutstandingBalance(),
                    loan.getStartDate(),
                    loan.getMaturityDate(),
                    loan.getStatus(),
                    loan.getCreatedAt(),
                    loan.getUpdatedAt(),
                    loan.getClosedAt());
        }
    }

    public record LoanBalanceResponse(
            String loanId,
            String loanNumber,
            LoanType loanType,
            BigDecimal outstandingBalance,
            BigDecimal emiAmount,
            LoanStatus status) {
        public static LoanBalanceResponse from(Loan loan) {
            return new LoanBalanceResponse(
                    loan.getLoanId(),
                    loan.getLoanNumber(),
                    loan.getLoanType(),
                    loan.getOutstandingBalance(),
                    loan.getEmiAmount(),
                    loan.getStatus());
        }
    }

    public record EmiScheduleResponse(
            String emiScheduleId,
            Integer installmentNumber,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal totalDue,
            BigDecimal amountPaid,
            EmiStatus status,
            Instant paidAt,
            Instant reminderSentAt,
            Instant overdueNotifiedAt) {
        public static EmiScheduleResponse from(EmiSchedule emi) {
            return new EmiScheduleResponse(
                    emi.getEmiScheduleId(),
                    emi.getInstallmentNumber(),
                    emi.getDueDate(),
                    emi.getOpeningBalance(),
                    emi.getPrincipalDue(),
                    emi.getInterestDue(),
                    emi.getTotalDue(),
                    emi.getAmountPaid(),
                    emi.getStatus(),
                    emi.getPaidAt(),
                    emi.getReminderSentAt(),
                    emi.getOverdueNotifiedAt());
        }
    }

    public record LoanRepaymentResponse(
            String loanRepaymentId,
            String loanId,
            String customerUserId,
            String sourceAccountId,
            BigDecimal amount,
            String workflowReference,
            String transactionId,
            String transactionReference,
            LoanRepaymentStatus status,
            String failureReason,
            BigDecimal principalApplied,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Instant reversedAt) {
        public static LoanRepaymentResponse from(LoanRepayment repayment) {
            return new LoanRepaymentResponse(
                    repayment.getLoanRepaymentId(),
                    repayment.getLoan().getLoanId(),
                    repayment.getCustomerUserId(),
                    repayment.getSourceAccountId(),
                    repayment.getAmount(),
                    repayment.getWorkflowReference(),
                    repayment.getTransactionId(),
                    repayment.getTransactionReference(),
                    repayment.getStatus(),
                    repayment.getFailureReason(),
                    repayment.getPrincipalApplied(),
                    repayment.getCreatedAt(),
                    repayment.getUpdatedAt(),
                    repayment.getCompletedAt(),
                    repayment.getReversedAt());
        }
    }

    public record EmiPreview(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal totalDue,
            BigDecimal closingBalance) {}

    public record EmiCalculationResponse(
            BigDecimal monthlyEmi,
            BigDecimal totalInterest,
            BigDecimal totalRepayment,
            List<EmiPreview> schedulePreview) {}

    public record InternalLoanValidationResponse(
            String loanId,
            String customerUserId,
            String linkedAccountId,
            BigDecimal outstandingBalance,
            LoanStatus status) {
        public static InternalLoanValidationResponse from(Loan loan) {
            return new InternalLoanValidationResponse(
                    loan.getLoanId(),
                    loan.getCustomerUserId(),
                    loan.getLinkedAccountId(),
                    loan.getOutstandingBalance(),
                    loan.getStatus());
        }
    }

    public record AccountValidationResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            String branchIfsc,
            String status,
            BigDecimal availableBalance,
            boolean active
    ) {}

    public record InternalCreateLoanRepaymentRequest(
            @NotBlank @Size(max = 36) String loanId,
            @NotBlank @Size(max = 36) String customerUserId,
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(max = 80) String workflowReference) {}

    public record InternalCompleteLoanRepaymentRequest(
            @NotBlank @Size(max = 36) String transactionId,
            @NotBlank @Size(max = 80) String transactionReference) {}

    public record InternalFailLoanRepaymentRequest(@Size(max = 500) String reason) {}

    public record InternalLoanMaintenanceRequest(
            @NotNull LocalDate businessDate,
            @NotBlank @Size(max = 160) String idempotencyKey) {}

    public record InternalLoanMaintenanceResponse(
            String operationType,
            LocalDate businessDate,
            int processed,
            int eventsPublished) {}
}
