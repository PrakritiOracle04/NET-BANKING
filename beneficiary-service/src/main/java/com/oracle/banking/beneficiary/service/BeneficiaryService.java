package com.oracle.banking.beneficiary.service;

import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryRequest;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiarySummaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryVerificationResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.UpdateBeneficiaryStatusRequest;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.VerifyBeneficiaryRequest;
import com.oracle.banking.beneficiary.entity.Beneficiary;
import com.oracle.banking.beneficiary.entity.BeneficiaryStatus;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.Duplicate;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.NotFound;
import com.oracle.banking.beneficiary.repository.BeneficiaryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {
    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository repository;

    public BeneficiaryService(BeneficiaryRepository repository) {
        this.repository = repository;
    }

    public List<BeneficiarySummaryResponse> list(String username, boolean favouritesOnly) {
        List<Beneficiary> beneficiaries = favouritesOnly
                ? repository.findByCustomerUsernameAndFavouriteTrue(username)
                : repository.findByCustomerUsername(username);
        return beneficiaries.stream().map(BeneficiarySummaryResponse::from).toList();
    }

    public BeneficiaryResponse byId(String id, String username, boolean admin) {
        Beneficiary beneficiary = admin
                ? find(id)
                : repository.findByBeneficiaryIdAndCustomerUsername(id, username).orElseThrow(() -> new NotFound("Beneficiary not found"));
        return BeneficiaryResponse.from(beneficiary);
    }

    @Transactional
    public BeneficiaryResponse create(String username, BeneficiaryRequest request) {
        if (repository.existsByCustomerUsernameAndNicknameIgnoreCase(username, request.nickname())) {
            throw new Duplicate("Beneficiary nickname already exists");
        }
        if (repository.findByCustomerUsernameAndAccountNumber(username, request.accountNumber()).isPresent()) {
            throw new Duplicate("Beneficiary account already exists");
        }
        Beneficiary beneficiary = new Beneficiary();
        apply(beneficiary, request);
        beneficiary.setCustomerUsername(username);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        Beneficiary saved = repository.save(beneficiary);
        log.info("Created beneficiary {} for customer {}", saved.getBeneficiaryId(), username);
        return BeneficiaryResponse.from(saved);
    }

    @Transactional
    public BeneficiaryResponse update(String id, String username, BeneficiaryRequest request) {
        Beneficiary beneficiary = repository.findByBeneficiaryIdAndCustomerUsername(id, username)
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        if (repository.existsByCustomerUsernameAndNicknameIgnoreCaseAndBeneficiaryIdNot(username, request.nickname(), id)) {
            throw new Duplicate("Beneficiary nickname already exists");
        }
        apply(beneficiary, request);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        log.info("Updated beneficiary {} for customer {}", id, username);
        return BeneficiaryResponse.from(repository.save(beneficiary));
    }

    @Transactional
    public void delete(String id, String username) {
        Beneficiary beneficiary = repository.findByBeneficiaryIdAndCustomerUsername(id, username)
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        repository.delete(beneficiary);
        log.info("Deleted beneficiary {} for customer {}", id, username);
    }

    @Transactional
    public BeneficiaryResponse updateStatus(String id, UpdateBeneficiaryStatusRequest request) {
        Beneficiary beneficiary = find(id);
        beneficiary.setStatus(request.status());
        log.info("Updated beneficiary {} status to {}", id, request.status());
        return BeneficiaryResponse.from(repository.save(beneficiary));
    }

    public BeneficiaryVerificationResponse verifyForTransfer(VerifyBeneficiaryRequest request) {
        Beneficiary beneficiary = repository.findByCustomerUsernameAndAccountNumber(request.customerUsername(), request.destinationAccountNumber())
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        return BeneficiaryVerificationResponse.from(beneficiary);
    }

    private Beneficiary find(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFound("Beneficiary not found"));
    }

    private void apply(Beneficiary beneficiary, BeneficiaryRequest request) {
        beneficiary.setNickname(request.nickname());
        beneficiary.setBeneficiaryName(request.beneficiaryName());
        beneficiary.setAccountId(request.accountId());
        beneficiary.setAccountNumber(request.accountNumber());
        beneficiary.setBankName(request.bankName());
        beneficiary.setIfscCode(request.ifscCode());
        beneficiary.setFavourite(request.favourite());
    }
}
