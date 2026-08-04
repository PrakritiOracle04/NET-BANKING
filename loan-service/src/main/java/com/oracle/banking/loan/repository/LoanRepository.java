package com.oracle.banking.loan.repository;

import com.oracle.banking.loan.entity.Loan;
import com.oracle.banking.loan.entity.LoanStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, String> {
    Optional<Loan> findByLoanIdAndCustomerUserId(String loanId, String customerUserId);
    List<Loan> findByCustomerUserIdOrderByCreatedAtDesc(String customerUserId);
    List<Loan> findByCustomerUserIdAndStatusOrderByCreatedAtDesc(String customerUserId, LoanStatus status);
    List<Loan> findByStatusOrderByCreatedAtDesc(LoanStatus status);
    List<Loan> findAllByOrderByCreatedAtDesc();
    boolean existsByLoanNumber(String loanNumber);
}
