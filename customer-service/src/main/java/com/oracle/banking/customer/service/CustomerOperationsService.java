package com.oracle.banking.customer.service;

import com.oracle.banking.customer.dto.CustomerOperationsDtos.CustomerItem;
import com.oracle.banking.customer.dto.CustomerOperationsDtos.CustomerPage;
import com.oracle.banking.customer.dto.CustomerOperationsDtos.CustomerSummary;
import com.oracle.banking.customer.entity.CustomerProfile;
import com.oracle.banking.customer.entity.KycStatus;
import com.oracle.banking.customer.repository.CustomerKycRepository;
import com.oracle.banking.customer.repository.CustomerProfileRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOperationsService {
    private final CustomerProfileRepository profiles;
    private final CustomerKycRepository kyc;

    public CustomerOperationsService(CustomerProfileRepository profiles, CustomerKycRepository kyc) {
        this.profiles = profiles;
        this.kyc = kyc;
    }

    @Transactional(readOnly = true)
    public CustomerPage search(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CustomerProfile> result = status == null || status.isBlank()
                ? profiles.findAll(pageable)
                : profiles.findByProfileStatus(status.toUpperCase(), pageable);
        List<CustomerItem> items = result.getContent().stream()
                .map(profile -> CustomerItem.from(profile, kyc.findByUserId(profile.getUserId()).orElse(null)))
                .toList();
        return CustomerPage.from(result, items);
    }

    @Transactional(readOnly = true)
    public CustomerSummary summary() {
        return new CustomerSummary(
                profiles.count(),
                profiles.countByProfileStatus("ACTIVE"),
                kyc.countByStatus(KycStatus.PENDING),
                kyc.countByStatus(KycStatus.VERIFIED),
                kyc.countByStatus(KycStatus.REJECTED));
    }
}
