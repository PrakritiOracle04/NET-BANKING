package com.oracle.banking.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "CARD")
public class Card {

    @Id
    @Column(name = "CARD_ID")
    private String cardId;

    @ManyToOne
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    @Column(name = "CARD_NUMBER_MASKED", nullable = false, length = 20)
    private String cardNumberMasked;

    @Column(name = "CARD_TYPE", nullable = false, length = 20)
    private String cardType;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "DAILY_LIMIT")
    private BigDecimal dailyLimit;

    public Card() {
    }

    public Card(String cardId, Account account, String cardNumberMasked,
                String cardType, String status, BigDecimal dailyLimit) {
        this.cardId = cardId;
        this.account = account;
        this.cardNumberMasked = cardNumberMasked;
        this.cardType = cardType;
        this.status = status;
        this.dailyLimit = dailyLimit;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getCardNumberMasked() {
        return cardNumberMasked;
    }

    public void setCardNumberMasked(String cardNumberMasked) {
        this.cardNumberMasked = cardNumberMasked;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    @Override
    public String toString() {
        return "Card{" +
                "cardId='" + cardId + '\'' +
                ", cardNumberMasked='" + cardNumberMasked + '\'' +
                ", cardType='" + cardType + '\'' +
                ", status='" + status + '\'' +
                ", dailyLimit=" + dailyLimit +
                '}';
    }
}