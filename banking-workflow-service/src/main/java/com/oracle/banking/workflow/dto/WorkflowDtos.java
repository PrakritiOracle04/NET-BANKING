package com.oracle.banking.workflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public final class WorkflowDtos {
    private WorkflowDtos() {}

    public record TransferRequest(
            @NotBlank String sourceAccountId,
            @NotBlank @Size(max = 30) String destinationAccountNumber,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record DepositRequest(
            @NotBlank String accountId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record WithdrawRequest(
            @NotBlank String accountId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record OpenAccountRequest(
            @NotBlank @Pattern(regexp = "^(SAVINGS|CURRENT|SALARY)$", message = "Invalid account type")
            String accountType,
            @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC")
            String branchIfsc
    ) {}

    public record OpenAccountResponse(
            String referenceNumber,
            String accountId,
            String accountNumber,
            String accountType,
            String branchIfsc,
            String status,
            boolean primaryAccount
    ) {}

    public record BillPaymentWorkflowRequest(
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotBlank @Size(max = 36) String customerBillerId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record BillPaymentWorkflowResponse(
            String referenceNumber,
            String billPaymentId,
            String transactionId,
            String sourceAccountId,
            BigDecimal amount,
            String status
    ) {}

    public record LoanRepaymentWorkflowRequest(
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record LoanRepaymentWorkflowResponse(
            String referenceNumber,
            String loanId,
            String loanRepaymentId,
            String transactionId,
            String sourceAccountId,
            BigDecimal amount,
            String status
    ) {}

    public record ScheduledBillPaymentWorkflowRequest(
            @NotBlank String customerUserId,
            @NotBlank String scheduleId,
            @NotNull Instant scheduledFor,
            @NotBlank String idempotencyKey,
            @NotBlank String sourceAccountId,
            @NotBlank String customerBillerId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Size(max = 160) String description
    ) {}

    public record LoanMaintenanceWorkflowRequest(
            @NotBlank String operationType,
            @NotNull Instant scheduledFor,
            @NotNull LocalDate businessDate,
            @NotBlank String idempotencyKey
    ) {}

    public record LoanMaintenanceResponse(String operationType, LocalDate businessDate, int processed, int eventsPublished) {}

    public record TransferResponse(
            String referenceNumber,
            String sourceAccountId,
            String destinationAccountId,
            BigDecimal amount,
            String status,
            String debitTransactionId,
            String creditTransactionId
    ) {}

    public record DepositResponse(String referenceNumber, String accountId, BigDecimal amount, String status, String transactionId) {}

    public record WithdrawResponse(String referenceNumber, String accountId, BigDecimal amount, String status, String transactionId) {}

    public record InternalAccountValidationResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            String branchIfsc,
            String status,
            BigDecimal availableBalance,
            boolean active
    ) {}

    public record CustomerOnboardingStatus(
            String userId,
            boolean profileComplete,
            String kycStatus,
            boolean eligibleForAccountOpening
    ) {}

    public record BranchResponse(String branchId, String branchName, String ifsc, String city, String state) {}

    public record InternalOpenAccountRequest(
            String customerUserId,
            String accountType,
            String branchIfsc,
            String openingReference
    ) {}

    public record InternalOpenAccountResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            String accountType,
            String branchIfsc,
            String status,
            BigDecimal availableBalance,
            BigDecimal ledgerBalance,
            boolean primaryAccount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record MoneyMovementRequest(BigDecimal amount, String referenceNumber, String description) {}

    public record BeneficiaryVerificationRequest(String customerUserId, String destinationAccountNumber) {}

    public record BeneficiaryVerificationResponse(
            String beneficiaryId,
            String customerUserId,
            String destinationAccountNumber,
            String status,
            boolean verified
    ) {}

    public record InternalBillerValidationResponse(
            String customerBillerId,
            String customerUserId,
            String billerId,
            String billerName,
            String consumerReference,
            boolean active
    ) {}

    public record InternalCreateBillPaymentRequest(
            String customerUserId,
            String customerBillerId,
            String sourceAccountId,
            BigDecimal amount,
            String workflowReference,
            String description
    ) {}

    public record InternalCompleteBillPaymentRequest(String transactionId, String transactionReference) {}

    public record InternalFailBillPaymentRequest(String reason) {}

    public record InternalBillPaymentResponse(
            String billPaymentId,
            String customerUserId,
            String customerBillerId,
            String billerId,
            String billerName,
            String consumerReference,
            String sourceAccountId,
            BigDecimal amount,
            String status,
            String workflowReference,
            String transactionId,
            String transactionReference,
            String description,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {}

    public record InternalLoanValidationResponse(
            String loanId,
            String customerUserId,
            String linkedAccountId,
            BigDecimal outstandingBalance,
            String status
    ) {}

    public record InternalCreateLoanRepaymentRequest(
            String loanId,
            String customerUserId,
            String sourceAccountId,
            BigDecimal amount,
            String workflowReference
    ) {}

    public record InternalCompleteLoanRepaymentRequest(String transactionId, String transactionReference) {}

    public record InternalFailLoanRepaymentRequest(String reason) {}

    public record InternalLoanMaintenanceRequest(LocalDate businessDate, String idempotencyKey) {}

    public record InternalLoanMaintenanceResponse(String operationType, LocalDate businessDate, int processed, int eventsPublished) {}

    public record InternalLoanRepaymentResponse(
            String loanRepaymentId,
            String loanId,
            String customerUserId,
            String sourceAccountId,
            BigDecimal amount,
            String workflowReference,
            String transactionId,
            String transactionReference,
            String status,
            String failureReason,
            BigDecimal principalApplied,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Instant reversedAt
    ) {}

    public record RecordTransactionRequest(
            String accountId,
            String accountNumber,
            String customerUserId,
            String transactionType,
            String referenceNumber,
            String referenceType,
            BigDecimal amount,
            String debitCredit,
            String status,
            String description,
            Instant transactionDate
    ) {}

    public record TransactionResponse(
            String transactionId,
            String accountId,
            String accountNumber,
            String customerUserId,
            String transactionType,
            String referenceNumber,
            String referenceType,
            BigDecimal amount,
            String status,
            String debitCredit,
            String description,
            Instant transactionDate
    ) {}

    public record DomainEvent(String eventType, String referenceNumber, String accountId, BigDecimal amount, String status, Instant occurredAt,
            String recipient, String templateName, Map<String, String> variables) {}
}
