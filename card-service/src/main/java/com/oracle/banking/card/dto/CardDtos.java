package com.oracle.banking.card.dto;

import com.oracle.banking.card.entity.BankCard;
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
            @NotNull @DecimalMin("0.01") BigDecimal dailyTransactionLimit
    ) {}

    public record CardBlockRequest(@Size(max = 240) String reason) {}

    public record CardLimitUpdateRequest(@NotNull @DecimalMin("0.01") BigDecimal dailyTransactionLimit) {}

    public record CardResponse(
            String cardId,
            String customerUserId,
            String accountId,
            String maskedCardNumber,
            CardType cardType,
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
                    "************" + card.getLastFourDigits(), card.getCardType(), card.getStatus(),
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
