package com.oracle.banking.loan.repository;

import com.oracle.banking.loan.entity.LoanApplication;
import com.oracle.banking.loan.entity.LoanApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, String>, JpaSpecificationExecutor<LoanApplication> {
    List<LoanApplication> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    Optional<LoanApplication> findByApplicationIdAndCustomerUserId(String applicationId, String customerUserId);
    boolean existsByCustomerUserIdAndLinkedAccountIdAndStatusIn(String customerUserId, String linkedAccountId, Collection<LoanApplicationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from LoanApplication application where application.applicationId = :applicationId")
    Optional<LoanApplication> findWithLockingByApplicationId(String applicationId);
}
