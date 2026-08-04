package com.oracle.banking.loan.repository;

import com.oracle.banking.loan.entity.EmiSchedule;
import com.oracle.banking.loan.entity.EmiStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmiScheduleRepository extends JpaRepository<EmiSchedule, String> {
    List<EmiSchedule> findByLoanLoanIdOrderByInstallmentNumberAsc(String loanId);
    List<EmiSchedule> findByLoanLoanIdAndStatusInOrderByInstallmentNumberAsc(String loanId, Collection<EmiStatus> statuses);
    boolean existsByLoanLoanIdAndStatus(String loanId, EmiStatus status);
    List<EmiSchedule> findByStatusInAndDueDate(Collection<EmiStatus> statuses, LocalDate dueDate);
    List<EmiSchedule> findByStatusInAndDueDateBefore(Collection<EmiStatus> statuses, LocalDate businessDate);
}
