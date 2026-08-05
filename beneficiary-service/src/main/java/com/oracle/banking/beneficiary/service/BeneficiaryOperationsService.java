package com.oracle.banking.beneficiary.service;

import com.oracle.banking.beneficiary.dto.BeneficiaryOperationsDtos.BeneficiaryPage;
import com.oracle.banking.beneficiary.dto.BeneficiaryOperationsDtos.BeneficiarySummary;
import com.oracle.banking.beneficiary.entity.Beneficiary;
import com.oracle.banking.beneficiary.entity.BeneficiaryStatus;
import com.oracle.banking.beneficiary.repository.BeneficiaryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryOperationsService {
    private final BeneficiaryRepository beneficiaries;

    public BeneficiaryOperationsService(BeneficiaryRepository beneficiaries) {
        this.beneficiaries = beneficiaries;
    }

    @Transactional(readOnly = true)
    public BeneficiaryPage search(String customerUserId, BeneficiaryStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Beneficiary> result;
        if (customerUserId != null && !customerUserId.isBlank() && status != null) {
            result = beneficiaries.findByCustomerUserIdAndStatus(customerUserId, status, pageable);
        } else if (customerUserId != null && !customerUserId.isBlank()) {
            result = beneficiaries.findByCustomerUserId(customerUserId, pageable);
        } else if (status != null) {
            result = beneficiaries.findByStatus(status, pageable);
        } else {
            result = beneficiaries.findAll(pageable);
        }
        return BeneficiaryPage.from(result);
    }

    @Transactional(readOnly = true)
    public BeneficiarySummary summary() {
        return new BeneficiarySummary(
                beneficiaries.count(),
                beneficiaries.countByStatus(BeneficiaryStatus.PENDING),
                beneficiaries.countByStatus(BeneficiaryStatus.VERIFIED),
                beneficiaries.countByStatus(BeneficiaryStatus.BLOCKED));
    }
}
