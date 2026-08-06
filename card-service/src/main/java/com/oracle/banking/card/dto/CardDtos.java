package com.oracle.banking.card.dto;

import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.card.entity.CardApplication;
import com.oracle.banking.card.entity.CardApplicationStatus;
import com.oracle.banking.card.entity.CardProduct;
import com.oracle.banking.card.entity.CardStatus;
import com.oracle.banking.card.entity.CardType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class CardDtos {
    private CardDtos() {}

    public record CardIssueRequest(
            @NotBlank @Size(max = 36) String customerUserId,
            @NotBlank @Size(max = 36) String accountId,
            @NotNull CardType cardType,
            CardProduct cardProduct,
            @NotNull @DecimalMin("0.01") BigDecimal dailyTransactionLimit
    ) {}

    public record CardProductResponse(
            CardProduct code,
            String label,
            BigDecimal minimumAnnualIncome,
            BigDecimal defaultDailyLimit
    ) {}

    public record CardApplicationRequest(
            @NotBlank @Size(max = 36) String accountId,
            @NotNull CardProduct cardProduct,
            @NotNull @DecimalMin("0.00") BigDecimal annualIncome,
            @Size(max = 120) String occupation,
            @NotBlank @Size(max = 500) String deliveryAddress,
            @DecimalMin("0.01") BigDecimal requestedDailyLimit
    ) {}

    public record CardApplicationApprovalRequest(
            @DecimalMin("0.01") BigDecimal approvedDailyLimit,
            @Size(max = 500) String notes
    ) {}

    public record CardApplicationRejectionRequest(
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record CardApplicationResponse(
            String applicationId,
            String customerUserId,
            String accountId,
            CardType cardType,
            CardProduct cardProduct,
            BigDecimal annualIncome,
            String occupation,
            String deliveryAddress,
            BigDecimal requestedDailyLimit,
            BigDecimal approvedDailyLimit,
            CardApplicationStatus status,
            String rejectionReason,
            String decisionNotes,
            String issuedCardId,
            String decidedByUserId,
            Instant createdAt,
            Instant updatedAt,
            Instant decidedAt
    ) {
        public static CardApplicationResponse from(CardApplication application) {
            return new CardApplicationResponse(
                    application.getApplicationId(), application.getCustomerUserId(), application.getAccountId(),
                    application.getCardType(), application.getCardProduct(), application.getAnnualIncome(),
                    application.getOccupation(), application.getDeliveryAddress(), application.getRequestedDailyLimit(),
                    application.getApprovedDailyLimit(), application.getStatus(), application.getRejectionReason(),
                    application.getDecisionNotes(), application.getIssuedCardId(), application.getDecidedByUserId(),
                    application.getCreatedAt(), application.getUpdatedAt(), application.getDecidedAt());
        }
    }

    public record CardBlockRequest(@Size(max = 240) String reason) {}

    public record CardLimitUpdateRequest(@NotNull @DecimalMin("0.01") BigDecimal dailyTransactionLimit) {}

    public record CardResponse(
            String cardId,
            String customerUserId,
            String accountId,
            String maskedCardNumber,
            CardType cardType,
            CardProduct cardProduct,
            CardStatus status,
            BigDecimal dailyTransactionLimit,
            int expiryMonth,
            int expiryYear,
            String blockedReason,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt,
            Instant blockedAt
    ) {
        public static CardResponse from(BankCard card) {
            return new CardResponse(
                    card.getCardId(), card.getCustomerUserId(), card.getAccountId(),
                    "************" + card.getLastFourDigits(), card.getCardType(), card.getCardProduct(), card.getStatus(),
                    card.getDailyTransactionLimit(), card.getExpiryMonth(), card.getExpiryYear(),
                    card.getBlockedReason(), card.getCreatedAt(), card.getUpdatedAt(),
                    card.getActivatedAt(), card.getBlockedAt());
        }
    }

    public record CardStatusResponse(
            String cardId,
            String maskedCardNumber,
            CardStatus status,
            BigDecimal dailyTransactionLimit,
            int expiryMonth,
            int expiryYear
    ) {
        public static CardStatusResponse from(BankCard card) {
            return new CardStatusResponse(
                    card.getCardId(), "************" + card.getLastFourDigits(), card.getStatus(),
                    card.getDailyTransactionLimit(), card.getExpiryMonth(), card.getExpiryYear());
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
}
