package com.oracle.banking.beneficiary.dto;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import com.oracle.banking.beneficiary.entity.BeneficiaryStatus;
import com.oracle.banking.beneficiary.entity.BeneficiaryRelationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.math.BigDecimal;

public final class BeneficiaryDtos {
    private BeneficiaryDtos() {}

    public record BeneficiaryRequest(
            @NotBlank @Size(max = 80) String nickname,
            @NotBlank @Size(max = 120) String beneficiaryName,
            @NotNull BeneficiaryRelationship relationship,
            @NotBlank @Size(max = 30) String accountNumber,
            @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC") String ifscCode,
            boolean favourite
    ) {}

    public record UpdateBeneficiaryStatusRequest(@NotNull BeneficiaryStatus status) {}

    public record BeneficiaryResponse(
            String beneficiaryId,
            String customerUserId,
            String nickname,
            String beneficiaryName,
            BeneficiaryRelationship relationship,
            String accountNumber,
            String ifscCode,
            BeneficiaryStatus status,
            boolean favourite,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static BeneficiaryResponse from(Beneficiary beneficiary) {
            return new BeneficiaryResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getCustomerUserId(),
                    beneficiary.getNickname(),
                    beneficiary.getBeneficiaryName(),
                    beneficiary.getRelationship(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getIfscCode(),
                    beneficiary.getStatus(),
                    beneficiary.isFavourite(),
                    beneficiary.getCreatedAt(),
                    beneficiary.getUpdatedAt()
            );
        }
    }

    public record BeneficiarySummaryResponse(
            String beneficiaryId,
            String nickname,
            String beneficiaryName,
            BeneficiaryRelationship relationship,
            String accountNumber,
            String ifscCode,
            BeneficiaryStatus status,
            boolean favourite
    ) {
        public static BeneficiarySummaryResponse from(Beneficiary beneficiary) {
            return new BeneficiarySummaryResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getNickname(),
                    beneficiary.getBeneficiaryName(),
                    beneficiary.getRelationship(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getIfscCode(),
                    beneficiary.getStatus(),
                    beneficiary.isFavourite()
            );
        }
    }

    public record VerifyBeneficiaryRequest(
            @NotBlank @Size(max = 36) String customerUserId,
            @NotBlank String destinationAccountNumber
    ) {}

    public record BeneficiaryVerificationResponse(
            String beneficiaryId,
            String customerUserId,
            String destinationAccountNumber,
            BeneficiaryStatus status,
            boolean verified
    ) {
        public static BeneficiaryVerificationResponse from(Beneficiary beneficiary) {
            return new BeneficiaryVerificationResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getCustomerUserId(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getStatus(),
                    beneficiary.getStatus() == BeneficiaryStatus.VERIFIED
            );
        }
    }

    public record AccountValidationResponse(
            String accountId,
            String customerUserId,
            String accountNumber,
            String branchIfsc,
            String status,
            BigDecimal availableBalance,
            boolean active
    ) {}
}
