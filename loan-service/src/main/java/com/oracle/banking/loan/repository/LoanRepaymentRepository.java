package com.oracle.banking.loan.repository;

import com.oracle.banking.loan.entity.LoanRepayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, String> {
    Optional<LoanRepayment> findByWorkflowReference(String workflowReference);
    List<LoanRepayment> findByLoanLoanIdOrderByCreatedAtDesc(String loanId);
}
