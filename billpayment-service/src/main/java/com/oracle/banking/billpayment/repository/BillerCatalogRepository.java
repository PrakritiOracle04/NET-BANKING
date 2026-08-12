package com.oracle.banking.billpayment.repository;

import com.oracle.banking.billpayment.entity.BillerCatalog;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.entity.BillerStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillerCatalogRepository extends JpaRepository<BillerCatalog, String> {
    boolean existsByBillerCodeIgnoreCase(String billerCode);
    Optional<BillerCatalog> findByBillerCodeIgnoreCase(String billerCode);
    boolean existsByBillerCodeIgnoreCaseAndBillerIdNot(String billerCode, String billerId);
    Optional<BillerCatalog> findByBillerIdAndStatus(String billerId, BillerStatus status);
    List<BillerCatalog> findByStatusOrderByBillerNameAsc(BillerStatus status);
    List<BillerCatalog> findByStatusAndCategoryOrderByBillerNameAsc(BillerStatus status, BillerCategory category);
}
