package com.oracle.banking.beneficiary.repository;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    List<Beneficiary> findByCustomerUserId(String customerUserId);
    List<Beneficiary> findByCustomerUserIdAndFavouriteTrue(String customerUserId);
    Optional<Beneficiary> findByBeneficiaryIdAndCustomerUserId(String beneficiaryId, String customerUserId);
    Optional<Beneficiary> findByCustomerUserIdAndAccountNumber(String customerUserId, String accountNumber);
    boolean existsByCustomerUserIdAndNicknameIgnoreCase(String customerUserId, String nickname);
    boolean existsByCustomerUserIdAndNicknameIgnoreCaseAndBeneficiaryIdNot(String customerUserId, String nickname, String beneficiaryId);
    Page<Beneficiary> findByCustomerUserId(String customerUserId, Pageable pageable);
    Page<Beneficiary> findByStatus(com.oracle.banking.beneficiary.entity.BeneficiaryStatus status, Pageable pageable);
    Page<Beneficiary> findByCustomerUserIdAndStatus(String customerUserId, com.oracle.banking.beneficiary.entity.BeneficiaryStatus status, Pageable pageable);
    long countByStatus(com.oracle.banking.beneficiary.entity.BeneficiaryStatus status);
}
