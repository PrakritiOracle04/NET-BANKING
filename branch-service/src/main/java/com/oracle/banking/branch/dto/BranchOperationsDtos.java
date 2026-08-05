package com.oracle.banking.branch.dto;

import com.oracle.banking.branch.entity.Branch;
import java.util.List;
import org.springframework.data.domain.Page;

public final class BranchOperationsDtos {
    private BranchOperationsDtos() {}

    public record BranchItem(String branchId, String branchName, String ifsc, String city, String state) {
        public static BranchItem from(Branch branch) {
            return new BranchItem(
                    branch.getBranchId(),
                    branch.getBranchName(),
                    branch.getIfsc(),
                    branch.getCity(),
                    branch.getState());
        }
    }

    public record BranchPage(List<BranchItem> items, int page, int size, long totalElements, int totalPages) {
        public static BranchPage from(Page<Branch> result) {
            return new BranchPage(
                    result.getContent().stream().map(BranchItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record BranchSummary(long total) {}
}
