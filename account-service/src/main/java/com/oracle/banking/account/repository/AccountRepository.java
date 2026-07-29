package com.oracle.banking.account.repository;

import com.oracle.banking.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
    List<Account> findByCustomerUsername(String customerUsername);
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByCustomerUsernameAndPrimaryAccountTrue(String customerUsername);
    boolean existsByAccountNumber(String accountNumber);
}
