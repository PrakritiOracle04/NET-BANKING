package com.oracle.banking.billpayment.service;

import com.oracle.banking.billpayment.dto.BillPaymentOperationsDtos.BillPaymentPage;
import com.oracle.banking.billpayment.dto.BillPaymentOperationsDtos.BillPaymentSummary;
import com.oracle.banking.billpayment.entity.BillPayment;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import com.oracle.banking.billpayment.repository.BillPaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class BillPaymentOperationsService {
    private final BillPaymentRepository repository;

    public BillPaymentOperationsService(BillPaymentRepository repository) {
        this.repository = repository;
    }

    public BillPaymentPage search(String customerUserId, BillPaymentStatus status, int page, int size) {
        Specification<BillPayment> spec = (root, query, builder) -> builder.conjunction();
        if (customerUserId != null && !customerUserId.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("customerUserId"), customerUserId));
        }
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        return BillPaymentPage.from(repository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    public BillPaymentSummary summary() {
        return new BillPaymentSummary(
                repository.count(), repository.countByStatus(BillPaymentStatus.PENDING),
                repository.countByStatus(BillPaymentStatus.SUCCESS), repository.countByStatus(BillPaymentStatus.FAILED),
                repository.countByStatus(BillPaymentStatus.CANCELLED));
    }
}
