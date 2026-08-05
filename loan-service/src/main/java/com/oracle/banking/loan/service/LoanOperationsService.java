package com.oracle.banking.loan.service;

import com.oracle.banking.loan.dto.LoanOperationsDtos.LoanPage;
import com.oracle.banking.loan.dto.LoanOperationsDtos.LoanSummary;
import com.oracle.banking.loan.entity.Loan;
import com.oracle.banking.loan.entity.LoanStatus;
import com.oracle.banking.loan.entity.LoanType;
import com.oracle.banking.loan.repository.LoanRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class LoanOperationsService {
    private final LoanRepository repository;

    public LoanOperationsService(LoanRepository repository) {
        this.repository = repository;
    }

    public LoanPage search(String customerUserId, LoanType loanType, LoanStatus status, int page, int size) {
        Specification<Loan> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (loanType != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("loanType"), loanType));
        if (status != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        return LoanPage.from(repository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    public LoanSummary summary() {
        return new LoanSummary(repository.count(), repository.countByStatus(LoanStatus.ACTIVE),
                repository.countByStatus(LoanStatus.OVERDUE), repository.countByStatus(LoanStatus.DEFAULTED),
                repository.countByStatus(LoanStatus.CLOSED));
    }
}
