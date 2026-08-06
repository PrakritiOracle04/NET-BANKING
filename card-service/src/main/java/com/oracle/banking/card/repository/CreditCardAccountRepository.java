package com.oracle.banking.card.repository;

import com.oracle.banking.card.entity.CreditCardAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardAccountRepository extends JpaRepository<CreditCardAccount, String> {
    List<CreditCardAccount> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    List<CreditCardAccount> findAllByOrderByCreatedAtDesc();
    Optional<CreditCardAccount> findByCardId(String cardId);
    Optional<CreditCardAccount> findByCardIdAndCustomerUserId(String cardId, String customerUserId);
    boolean existsByCardId(String cardId);
}
