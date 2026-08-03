package com.oracle.banking.card.repository;

import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.card.entity.CardStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<BankCard, String> {
    List<BankCard> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    List<BankCard> findAllByOrderByCreatedAtDesc();
    Optional<BankCard> findByCardIdAndCustomerUserId(String cardId, String customerUserId);
    boolean existsByAccountIdAndStatusIn(String accountId, Collection<CardStatus> statuses);
    boolean existsByCardNumberHash(String cardNumberHash);
}
