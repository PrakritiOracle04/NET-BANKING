package com.oracle.banking.card.repository;

import com.oracle.banking.card.entity.BankCard;
import com.oracle.banking.card.entity.CardStatus;
import com.oracle.banking.card.entity.CardType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardRepository extends JpaRepository<BankCard, String>, JpaSpecificationExecutor<BankCard> {
    List<BankCard> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    List<BankCard> findAllByOrderByCreatedAtDesc();
    Optional<BankCard> findByCardIdAndCustomerUserId(String cardId, String customerUserId);
    boolean existsByAccountIdAndStatusIn(String accountId, Collection<CardStatus> statuses);
    boolean existsByAccountIdAndCardTypeAndStatusIn(String accountId, CardType cardType, Collection<CardStatus> statuses);
    boolean existsByCardNumberHash(String cardNumberHash);
    long countByStatus(CardStatus status);
}
