package com.oracle.banking.billpayment.repository;

import com.oracle.banking.billpayment.entity.CustomerBiller;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerBillerRepository extends JpaRepository<CustomerBiller, String> {
    List<CustomerBiller> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    Optional<CustomerBiller> findByCustomerBillerIdAndCustomerUserId(String customerBillerId, String customerUserId);
    boolean existsByCustomerUserIdAndBillerBillerIdAndConsumerReferenceIgnoreCase(
            String customerUserId, String billerId, String consumerReference);
    boolean existsByCustomerUserIdAndBillerBillerIdAndConsumerReferenceIgnoreCaseAndCustomerBillerIdNot(
            String customerUserId, String billerId, String consumerReference, String customerBillerId);
}
