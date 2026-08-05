package com.oracle.banking.billpayment.repository;

import com.oracle.banking.billpayment.entity.BillPayment;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BillPaymentRepository extends JpaRepository<BillPayment, String>, JpaSpecificationExecutor<BillPayment> {
    Optional<BillPayment> findByWorkflowReference(String workflowReference);
    Optional<BillPayment> findByBillPaymentIdAndCustomerUserId(String billPaymentId, String customerUserId);
    long countByStatus(BillPaymentStatus status);
}
