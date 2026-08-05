package com.oracle.banking.account.repository;

import com.oracle.banking.account.entity.Account;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
    List<Account> findByCustomerUserId(String customerUserId);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByCustomerUserIdAndPrimaryAccountTrue(String customerUserId);
    Optional<Account> findByOpeningReference(String openingReference);
    boolean existsByAccountNumber(String accountNumber);
    long countByCustomerUserId(String customerUserId);
    Page<Account> findByCustomerUserId(String customerUserId, Pageable pageable);
    Page<Account> findByStatus(com.oracle.banking.account.entity.AccountStatus status, Pageable pageable);
    Page<Account> findByCustomerUserIdAndStatus(String customerUserId, com.oracle.banking.account.entity.AccountStatus status, Pageable pageable);
    long countByStatus(com.oracle.banking.account.entity.AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.accountId = :accountId")
    Optional<Account> findLockedByAccountId(String accountId);
}
