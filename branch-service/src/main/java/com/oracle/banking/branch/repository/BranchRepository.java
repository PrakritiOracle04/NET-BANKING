package com.oracle.banking.branch.repository;

import com.oracle.banking.branch.entity.Branch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, String> {
    Optional<Branch> findByIfsc(String ifsc);
}
