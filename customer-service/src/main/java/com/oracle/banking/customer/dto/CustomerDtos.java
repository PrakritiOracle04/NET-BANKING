package com.oracle.banking.customer.dto;

import com.oracle.banking.customer.entity.CustomerProfile;
import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.KycStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Instant;

public final class CustomerDtos {
    private CustomerDtos() {}

    public record Create(
            @NotBlank @Size(max = 36) String userId,
            @NotBlank @Size(max = 120) String fullName
    ) {}

    public record Update(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 120) String fatherOrSpouseName,
            @Past LocalDate dateOfBirth,
            @NotBlank @Size(max = 160) String addressLine1,
            @Size(max = 160) String addressLine2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 80) String country,
            @NotBlank @Size(max = 20) String postalCode
    ) {}

    public record Response(
            String customerId,
            String userId,
            String fullName,
            String fatherOrSpouseName,
            LocalDate dateOfBirth,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String country,
            String postalCode,
            String profileStatus
    ) {
        public static Response from(CustomerProfile profile) {
            return new Response(
                    profile.getCustomerId(),
                    profile.getUserId(),
                    profile.getFullName(),
                    profile.getFatherOrSpouseName(),
                    profile.getDateOfBirth(),
                    profile.getAddressLine1(),
                    profile.getAddressLine2(),
                    profile.getCity(),
                    profile.getState(),
                    profile.getCountry(),
                    profile.getPostalCode(),
                    profile.getProfileStatus());
        }
    }

    public record KycSubmission(
            @NotBlank @Pattern(regexp = "^[0-9]{12}$", message = "Aadhaar must contain exactly 12 digits")
            String aadhaarNumber,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{5}[0-9]{4}[A-Za-z]$", message = "Invalid PAN format")
            String panNumber
    ) {}

    public record KycStatusUpdate(
            @NotNull KycStatus status,
            @Size(max = 240) String rejectionReason
    ) {}

    public record KycResponse(
            String kycId,
            String userId,
            String maskedAadhaar,
            String maskedPan,
            KycStatus status,
            String rejectionReason,
            Instant verifiedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static KycResponse from(CustomerKyc kyc) {
            return new KycResponse(
                    kyc.getKycId(),
                    kyc.getUserId(),
                    "XXXXXXXX" + kyc.getAadhaarLast4(),
                    "XXXXXX" + kyc.getPanLast4(),
                    kyc.getStatus(),
                    kyc.getRejectionReason(),
                    kyc.getVerifiedAt(),
                    kyc.getCreatedAt(),
                    kyc.getUpdatedAt());
        }
    }

    public record OnboardingStatus(
            String userId,
            boolean profileComplete,
            KycStatus kycStatus,
            boolean eligibleForAccountOpening
    ) {}
}
