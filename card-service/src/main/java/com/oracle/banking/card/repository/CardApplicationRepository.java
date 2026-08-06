package com.oracle.banking.card.repository;

import com.oracle.banking.card.entity.CardApplication;
import com.oracle.banking.card.entity.CardApplicationStatus;
import com.oracle.banking.card.entity.CardType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CardApplicationRepository extends JpaRepository<CardApplication, String>, JpaSpecificationExecutor<CardApplication> {
    List<CardApplication> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    Optional<CardApplication> findByApplicationIdAndCustomerUserId(String applicationId, String customerUserId);
    boolean existsByCustomerUserIdAndAccountIdAndStatusIn(String customerUserId, String accountId, Collection<CardApplicationStatus> statuses);
    boolean existsByCustomerUserIdAndAccountIdAndCardTypeAndStatusIn(String customerUserId, String accountId, CardType cardType, Collection<CardApplicationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from CardApplication application where application.applicationId = :applicationId")
    Optional<CardApplication> findWithLockingByApplicationId(String applicationId);
}
