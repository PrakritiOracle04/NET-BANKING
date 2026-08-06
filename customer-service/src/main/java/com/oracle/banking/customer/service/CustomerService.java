package com.oracle.banking.customer.service;

import com.oracle.banking.customer.dto.CustomerDtos.Create;
import com.oracle.banking.customer.dto.CustomerDtos.KycResponse;
import com.oracle.banking.customer.dto.CustomerDtos.KycStatusUpdate;
import com.oracle.banking.customer.dto.CustomerDtos.KycSubmission;
import com.oracle.banking.customer.dto.CustomerDtos.OnboardingStatus;
import com.oracle.banking.customer.dto.CustomerDtos.Response;
import com.oracle.banking.customer.dto.CustomerDtos.Update;
import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.CustomerProfile;
import com.oracle.banking.customer.entity.KycStatus;
import com.oracle.banking.customer.entity.KycDocumentType;
import com.oracle.banking.customer.exception.CustomerExceptions.BadRequest;
import com.oracle.banking.customer.exception.CustomerExceptions.Duplicate;
import com.oracle.banking.customer.exception.CustomerExceptions.NotFound;
import com.oracle.banking.customer.repository.CustomerKycRepository;
import com.oracle.banking.customer.repository.CustomerProfileRepository;
import com.oracle.banking.customer.repository.KycDocumentRepository;
import com.oracle.banking.customer.event.CustomerAuditPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerProfileRepository profiles;
    private final CustomerKycRepository kycRecords;
    private final KycCrypto crypto;
    private final CustomerAuditPublisher auditEvents;
    private final KycDocumentRepository documents;

    public CustomerService(CustomerProfileRepository profiles, CustomerKycRepository kycRecords, KycCrypto crypto,
            CustomerAuditPublisher auditEvents, KycDocumentRepository documents) {
        this.profiles = profiles;
        this.kycRecords = kycRecords;
        this.crypto = crypto;
        this.auditEvents = auditEvents;
        this.documents = documents;
    }

    @Transactional
    public Response create(Create request) {
        if (profiles.existsByUserId(request.userId())) {
            throw new Duplicate("Customer profile already exists");
        }
        return Response.from(profiles.save(new CustomerProfile(request.userId(), request.fullName())));
    }

    public Response own(String userId) {
        return Response.from(requiredByUserId(userId));
    }

    @Transactional
    public Response update(String userId, Update request) {
        CustomerProfile profile = requiredByUserId(userId);
        profile.update(request);
        return Response.from(profile);
    }

    public Response byId(String customerId) {
        return Response.from(profiles.findById(customerId)
                .orElseThrow(() -> new NotFound("Customer profile not found")));
    }

    @Transactional
    public KycResponse submitKyc(String userId, KycSubmission request) {
        requiredByUserId(userId);
        String aadhaar = request.aadhaarNumber().trim();
        String pan = request.panNumber().trim().toUpperCase();
        String aadhaarHash = crypto.fingerprint(aadhaar);
        String panHash = crypto.fingerprint(pan);
        if (kycRecords.existsByAadhaarHashAndUserIdNot(aadhaarHash, userId)) {
            throw new Duplicate("Aadhaar is already associated with another customer");
        }
        if (kycRecords.existsByPanHashAndUserIdNot(panHash, userId)) {
            throw new Duplicate("PAN is already associated with another customer");
        }
        CustomerKyc kyc = kycRecords.findByUserId(userId).orElseGet(() -> new CustomerKyc(userId));
        kyc.submit(
                crypto.encrypt(aadhaar),
                aadhaar.substring(aadhaar.length() - 4),
                aadhaarHash,
                crypto.encrypt(pan),
                pan.substring(pan.length() - 4),
                panHash);
        CustomerKyc saved = kycRecords.save(kyc);
        auditEvents.submitted(saved);
        return KycResponse.from(saved);
    }

    public KycResponse ownKyc(String userId) {
        return KycResponse.from(requiredKyc(userId));
    }

    @Transactional
    public KycResponse updateKycStatus(String userId, KycStatusUpdate request) {
        CustomerKyc kyc = requiredKyc(userId);
        if (request.status() == KycStatus.VERIFIED) {
            if (!documents.existsByUserIdAndDocumentType(userId, KycDocumentType.AADHAAR)
                    || !documents.existsByUserIdAndDocumentType(userId, KycDocumentType.PAN)) {
                throw new BadRequest("Aadhaar and PAN documents are required before KYC verification");
            }
            kyc.verify();
        } else if (request.status() == KycStatus.REJECTED) {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new BadRequest("Rejection reason is required");
            }
            kyc.reject(request.rejectionReason().trim());
        } else {
            throw new BadRequest("KYC status can only be changed to VERIFIED or REJECTED");
        }
        auditEvents.statusChanged(kyc);
        return KycResponse.from(kyc);
    }

    public OnboardingStatus onboardingStatus(String userId) {
        CustomerProfile profile = requiredByUserId(userId);
        KycStatus kycStatus = kycRecords.findByUserId(userId)
                .map(CustomerKyc::getStatus)
                .orElse(KycStatus.PENDING);
        boolean eligible = profile.isComplete() && kycStatus == KycStatus.VERIFIED;
        return new OnboardingStatus(userId, profile.isComplete(), kycStatus, eligible);
    }

    CustomerProfile requiredByUserId(String userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFound("Customer profile not found"));
    }

    private CustomerKyc requiredKyc(String userId) {
        return kycRecords.findByUserId(userId)
                .orElseThrow(() -> new NotFound("KYC record not found"));
    }
}
