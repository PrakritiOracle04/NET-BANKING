package com.oracle.banking.workflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
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
            String customerUsername,
            String accountNumber,
            String status,
            BigDecimal availableBalance,
            boolean active
    ) {}

    public record MoneyMovementRequest(BigDecimal amount, String referenceNumber, String description) {}

    public record BeneficiaryVerificationRequest(String customerUsername, String destinationAccountNumber) {}

    public record BeneficiaryVerificationResponse(
            String beneficiaryId,
            String customerUsername,
            String destinationAccountNumber,
            String status,
            boolean verified
    ) {}

    public record RecordTransactionRequest(
            String accountId,
            String accountNumber,
            String customerUsername,
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
            String customerUsername,
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
