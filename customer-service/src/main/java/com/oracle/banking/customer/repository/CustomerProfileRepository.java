package com.oracle.banking.customer.repository;

import com.oracle.banking.customer.entity.CustomerProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {
    Optional<CustomerProfile> findByUserId(String userId);
    boolean existsByUserId(String userId);
    Page<CustomerProfile> findByProfileStatus(String status, Pageable pageable);
    long countByProfileStatus(String status);
}
