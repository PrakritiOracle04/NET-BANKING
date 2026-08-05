package com.oracle.banking.beneficiary.dto;

import com.oracle.banking.beneficiary.entity.Beneficiary;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class BeneficiaryOperationsDtos {
    private BeneficiaryOperationsDtos() {}

    public record BeneficiaryItem(
            String beneficiaryId,
            String customerUserId,
            String nickname,
            String beneficiaryName,
            String relationship,
            String maskedAccountNumber,
            String ifscCode,
            String status,
            boolean favourite,
            Instant createdAt,
            Instant updatedAt) {
        public static BeneficiaryItem from(Beneficiary beneficiary) {
            String number = beneficiary.getAccountNumber();
            String masked = number == null || number.length() <= 4
                    ? "****"
                    : "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
            return new BeneficiaryItem(
                    beneficiary.getBeneficiaryId(), beneficiary.getCustomerUserId(), beneficiary.getNickname(),
                    beneficiary.getBeneficiaryName(), beneficiary.getRelationship().name(), masked,
                    beneficiary.getIfscCode(), beneficiary.getStatus().name(), beneficiary.isFavourite(),
                    beneficiary.getCreatedAt(), beneficiary.getUpdatedAt());
        }
    }

    public record BeneficiaryPage(List<BeneficiaryItem> items, int page, int size, long totalElements, int totalPages) {
        public static BeneficiaryPage from(Page<Beneficiary> result) {
            return new BeneficiaryPage(result.getContent().stream().map(BeneficiaryItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record BeneficiarySummary(long total, long pending, long verified, long blocked) {}
}
