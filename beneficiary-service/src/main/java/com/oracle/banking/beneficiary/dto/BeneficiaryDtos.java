package com.oracle.banking.beneficiary.dto;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import com.oracle.banking.beneficiary.entity.BeneficiaryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BeneficiaryDtos {
    private BeneficiaryDtos() {}

    public record BeneficiaryRequest(
            @NotBlank @Size(max = 80) String nickname,
            @NotBlank @Size(max = 120) String beneficiaryName,
            @Size(max = 36) String accountId,
            @NotBlank @Size(max = 30) String accountNumber,
            @Size(max = 120) String bankName,
            @Size(max = 20) String ifscCode,
            boolean favourite
    ) {}

    public record UpdateBeneficiaryStatusRequest(@NotNull BeneficiaryStatus status) {}

    public record BeneficiaryResponse(
            String beneficiaryId,
            String customerUsername,
            String nickname,
            String beneficiaryName,
            String accountId,
            String accountNumber,
            String bankName,
            String ifscCode,
            BeneficiaryStatus status,
            boolean favourite,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static BeneficiaryResponse from(Beneficiary beneficiary) {
            return new BeneficiaryResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getCustomerUsername(),
                    beneficiary.getNickname(),
                    beneficiary.getBeneficiaryName(),
                    beneficiary.getAccountId(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getBankName(),
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
            String accountNumber,
            String bankName,
            BeneficiaryStatus status,
            boolean favourite
    ) {
        public static BeneficiarySummaryResponse from(Beneficiary beneficiary) {
            return new BeneficiarySummaryResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getNickname(),
                    beneficiary.getBeneficiaryName(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getBankName(),
                    beneficiary.getStatus(),
                    beneficiary.isFavourite()
            );
        }
    }

    public record VerifyBeneficiaryRequest(
            @NotBlank String customerUsername,
            @NotBlank String destinationAccountNumber
    ) {}

    public record BeneficiaryVerificationResponse(
            String beneficiaryId,
            String customerUsername,
            String destinationAccountNumber,
            BeneficiaryStatus status,
            boolean verified
    ) {
        public static BeneficiaryVerificationResponse from(Beneficiary beneficiary) {
            return new BeneficiaryVerificationResponse(
                    beneficiary.getBeneficiaryId(),
                    beneficiary.getCustomerUsername(),
                    beneficiary.getAccountNumber(),
                    beneficiary.getStatus(),
                    beneficiary.getStatus() == BeneficiaryStatus.VERIFIED
            );
        }
    }
}
