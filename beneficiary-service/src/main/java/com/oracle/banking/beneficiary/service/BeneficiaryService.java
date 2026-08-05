package com.oracle.banking.beneficiary.service;

import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryRequest;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiarySummaryResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.BeneficiaryVerificationResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.AccountValidationResponse;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.UpdateBeneficiaryStatusRequest;
import com.oracle.banking.beneficiary.dto.BeneficiaryDtos.VerifyBeneficiaryRequest;
import com.oracle.banking.beneficiary.entity.Beneficiary;
import com.oracle.banking.beneficiary.entity.BeneficiaryStatus;
import com.oracle.banking.beneficiary.entity.BeneficiaryRelationship;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.BadRequest;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.Duplicate;
import com.oracle.banking.beneficiary.exception.BeneficiaryExceptions.NotFound;
import com.oracle.banking.beneficiary.repository.BeneficiaryRepository;
import com.oracle.banking.beneficiary.event.BeneficiaryAuditPublisher;
import com.oracle.banking.shared.constants.SecurityConstants;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BeneficiaryService {
    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository repository;
    private final RestClient accountClient;
    private final String internalApiKey;
    private final BeneficiaryAuditPublisher auditEvents;

    public BeneficiaryService(
            BeneficiaryRepository repository,
            RestClient.Builder restClientBuilder,
            @Value("${services.account-service-url}") String accountServiceUrl,
            @Value("${services.internal-api-key}") String internalApiKey,
            BeneficiaryAuditPublisher auditEvents) {
        this.repository = repository;
        this.accountClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.internalApiKey = internalApiKey;
        this.auditEvents = auditEvents;
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
        validateDestination(userId, request);
        Beneficiary beneficiary = new Beneficiary();
        apply(beneficiary, request);
        beneficiary.setCustomerUserId(userId);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        Beneficiary saved = repository.save(beneficiary);
        auditEvents.publish("CREATED", saved);
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
        validateDestination(userId, request);
        apply(beneficiary, request);
        beneficiary.setStatus(BeneficiaryStatus.PENDING);
        log.info("Updated beneficiary {} for customer user ID {}", id, userId);
        Beneficiary saved = repository.save(beneficiary);
        auditEvents.publish("UPDATED", saved);
        return BeneficiaryResponse.from(saved);
    }

    @Transactional
    public void delete(String id, String userId) {
        Beneficiary beneficiary = repository.findByBeneficiaryIdAndCustomerUserId(id, userId)
                .orElseThrow(() -> new NotFound("Beneficiary not found"));
        repository.delete(beneficiary);
        auditEvents.publish("DELETED", beneficiary);
        log.info("Deleted beneficiary {} for customer user ID {}", id, userId);
    }

    @Transactional
    public BeneficiaryResponse updateStatus(String id, UpdateBeneficiaryStatusRequest request) {
        Beneficiary beneficiary = find(id);
        beneficiary.setStatus(request.status());
        log.info("Updated beneficiary {} status to {}", id, request.status());
        Beneficiary saved = repository.save(beneficiary);
        auditEvents.publish("STATUS_CHANGED", saved);
        return BeneficiaryResponse.from(saved);
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
        beneficiary.setRelationship(request.relationship());
        beneficiary.setAccountNumber(request.accountNumber());
        beneficiary.setIfscCode(request.ifscCode());
        beneficiary.setFavourite(request.favourite());
    }

    private void validateDestination(String userId, BeneficiaryRequest request) {
        AccountValidationResponse account;
        try {
            account = accountClient.get()
                    .uri("/internal/accounts/number/{accountNumber}/validate", request.accountNumber())
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(AccountValidationResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BadRequest("Beneficiary account could not be validated");
            }
            throw new BadRequest("Account service is unavailable");
        } catch (RestClientException exception) {
            throw new BadRequest("Account service is unavailable");
        }
        if (account == null || !account.active()) {
            throw new BadRequest("Beneficiary account must be active");
        }
        if (!request.ifscCode().equals(account.branchIfsc())) {
            throw new BadRequest("IFSC does not match the beneficiary account");
        }
        boolean selfOwned = userId.equals(account.customerUserId());
        if (selfOwned && request.relationship() != BeneficiaryRelationship.SELF) {
            throw new BadRequest("Use SELF relationship for your own account");
        }
        if (!selfOwned && request.relationship() == BeneficiaryRelationship.SELF) {
            throw new BadRequest("SELF relationship requires an account owned by the customer");
        }
    }
}
