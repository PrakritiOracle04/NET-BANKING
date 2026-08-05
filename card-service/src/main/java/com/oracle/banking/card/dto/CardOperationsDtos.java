package com.oracle.banking.card.dto;

import com.oracle.banking.card.entity.BankCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class CardOperationsDtos {
    private CardOperationsDtos() {}

    public record CardItem(
            String cardId, String customerUserId, String accountId, String maskedCardNumber,
            String cardType, String status, BigDecimal dailyTransactionLimit,
            int expiryMonth, int expiryYear, Instant createdAt, Instant updatedAt) {
        public static CardItem from(BankCard card) {
            return new CardItem(
                    card.getCardId(), card.getCustomerUserId(), card.getAccountId(),
                    "************" + card.getLastFourDigits(), card.getCardType().name(), card.getStatus().name(),
                    card.getDailyTransactionLimit(), card.getExpiryMonth(), card.getExpiryYear(),
                    card.getCreatedAt(), card.getUpdatedAt());
        }
    }

    public record CardPage(List<CardItem> items, int page, int size, long totalElements, int totalPages) {
        public static CardPage from(Page<BankCard> result) {
            return new CardPage(result.getContent().stream().map(CardItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record CardSummary(long total, long inactive, long active, long blocked, long expired) {}
}
