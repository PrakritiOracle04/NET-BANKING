package com.oracle.banking.beneficiary.repository;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    List<Beneficiary> findByCustomerUserId(String customerUserId);
    List<Beneficiary> findByCustomerUserIdAndFavouriteTrue(String customerUserId);
    Optional<Beneficiary> findByBeneficiaryIdAndCustomerUserId(String beneficiaryId, String customerUserId);
    Optional<Beneficiary> findByCustomerUserIdAndAccountNumber(String customerUserId, String accountNumber);
    boolean existsByCustomerUserIdAndNicknameIgnoreCase(String customerUserId, String nickname);
    boolean existsByCustomerUserIdAndNicknameIgnoreCaseAndBeneficiaryIdNot(String customerUserId, String nickname, String beneficiaryId);
}
