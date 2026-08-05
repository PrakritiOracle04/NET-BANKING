package com.oracle.banking.customer.repository;

import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.KycStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerKycRepository extends JpaRepository<CustomerKyc, String> {
    Optional<CustomerKyc> findByUserId(String userId);
    boolean existsByAadhaarHashAndUserIdNot(String aadhaarHash, String userId);
    boolean existsByPanHashAndUserIdNot(String panHash, String userId);
    long countByStatus(KycStatus status);
}
