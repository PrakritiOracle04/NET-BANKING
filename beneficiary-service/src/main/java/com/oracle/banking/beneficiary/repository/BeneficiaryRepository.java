package com.oracle.banking.beneficiary.repository;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    List<Beneficiary> findByCustomerUsername(String customerUsername);
    List<Beneficiary> findByCustomerUsernameAndFavouriteTrue(String customerUsername);
    Optional<Beneficiary> findByBeneficiaryIdAndCustomerUsername(String beneficiaryId, String customerUsername);
    Optional<Beneficiary> findByCustomerUsernameAndAccountNumber(String customerUsername, String accountNumber);
    boolean existsByCustomerUsernameAndNicknameIgnoreCase(String customerUsername, String nickname);
    boolean existsByCustomerUsernameAndNicknameIgnoreCaseAndBeneficiaryIdNot(String customerUsername, String nickname, String beneficiaryId);
}
