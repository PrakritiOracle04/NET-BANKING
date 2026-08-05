package com.oracle.banking.branch.service;

import com.oracle.banking.branch.dto.BranchOperationsDtos.BranchPage;
import com.oracle.banking.branch.dto.BranchOperationsDtos.BranchSummary;
import com.oracle.banking.branch.repository.BranchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchOperationsService {
    private final BranchRepository branches;

    public BranchOperationsService(BranchRepository branches) {
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public BranchPage search(int page, int size) {
        return BranchPage.from(branches.findAll(PageRequest.of(page, size, Sort.by("branchName"))));
    }

    @Transactional(readOnly = true)
    public BranchSummary summary() {
        return new BranchSummary(branches.count());
    }
}
