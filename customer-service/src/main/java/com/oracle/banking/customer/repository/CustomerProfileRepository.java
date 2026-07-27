package com.oracle.banking.customer.repository;
import com.oracle.banking.customer.entity.CustomerProfile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile,String>{Optional<CustomerProfile> findByUserId(String userId);boolean existsByUserId(String userId);}
