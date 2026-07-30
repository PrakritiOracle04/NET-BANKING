package com.oracle.banking.account.repository;

import com.oracle.banking.account.entity.AccountMovement;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountMovementRepository extends JpaRepository<AccountMovement, String> {
    Optional<AccountMovement> findByAccountIdAndReferenceNumber(String accountId, String referenceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select movement from AccountMovement movement where movement.accountId = :accountId and movement.referenceNumber = :referenceNumber")
    Optional<AccountMovement> findLockedByAccountIdAndReferenceNumber(String accountId, String referenceNumber);
}
