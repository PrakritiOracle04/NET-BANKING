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

    public List<BeneficiarySummaryResponse> list(String userId, boolean favouritesOnly) {
        List<Beneficiary> beneficiaries = favouritesOnly
                ? repository.findByCustomerUserIdAndFavouriteTrue(userId)
                : repository.findByCustomerUserId(userId);
        return beneficiaries.stream().map(BeneficiarySummaryResponse::from).toList();
    }

    public BeneficiaryResponse byId(String id, String userId, boolean admin) {
        Beneficiary beneficiary = admin
                ? find(id)
                : repository.findByBeneficiaryIdAndCustomerUserId(id, userId).orElseThrow(() -> new NotFound("Beneficiary not found"));
        return BeneficiaryResponse.from(beneficiary);
    }

    @Transactional
    public BeneficiaryResponse create(String userId, BeneficiaryRequest request) {
        if (repository.existsByCustomerUserIdAndNicknameIgnoreCase(userId, request.nickname())) {
            throw new Duplicate("Beneficiary nickname already exists");
        }
        if (repository.findByCustomerUserIdAndAccountNumber(userId, request.accountNumber()).isPresent()) {
            throw new Duplicate("Beneficiary account already exists");
        }
        Beneficiary beneficiary = new Beneficiary();
        apply(beneficiary, request);
        beneficiary.setCustomerUserId(userId);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        Beneficiary saved = repository.save(beneficiary);
        log.info("Created beneficiary {} for customer user ID {}", saved.getBeneficiaryId(), userId);
        return BeneficiaryResponse.from(saved);
    }

    @Transactional
    public BeneficiaryResponse update(String id, String userId, BeneficiaryRequest request) {
        Beneficiary beneficiary = repository.findByBeneficiaryIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        if (repository.existsByCustomerUserIdAndNicknameIgnoreCaseAndBeneficiaryIdNot(userId, request.nickname(), id)) {
            throw new Duplicate("Beneficiary nickname already exists");
        }
        apply(beneficiary, request);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        log.info("Updated beneficiary {} for customer user ID {}", id, userId);
        return BeneficiaryResponse.from(repository.save(beneficiary));
    }

    @Transactional
    public void delete(String id, String userId) {
        Beneficiary beneficiary = repository.findByBeneficiaryIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        repository.delete(beneficiary);
        log.info("Deleted beneficiary {} for customer user ID {}", id, userId);
    }

    @Transactional
    public BeneficiaryResponse updateStatus(String id, UpdateBeneficiaryStatusRequest request) {
        Beneficiary beneficiary = find(id);
        beneficiary.setStatus(request.status());
        log.info("Updated beneficiary {} status to {}", id, request.status());
        return BeneficiaryResponse.from(repository.save(beneficiary));
    }

    public BeneficiaryVerificationResponse verifyForTransfer(VerifyBeneficiaryRequest request) {
        Beneficiary beneficiary = repository.findByCustomerUserIdAndAccountNumber(request.customerUserId(), request.destinationAccountNumber())
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
