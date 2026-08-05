package com.oracle.banking.transaction.repository;

import com.oracle.banking.transaction.entity.BankTransaction;
import com.oracle.banking.transaction.entity.TransactionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, String>, JpaSpecificationExecutor<BankTransaction> {
    Optional<BankTransaction> findByReferenceNumber(String referenceNumber);
    boolean existsByReferenceNumber(String referenceNumber);
    long countByStatus(TransactionStatus status);
}
