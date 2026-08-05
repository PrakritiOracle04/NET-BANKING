package com.oracle.banking.customer.dto;

import com.oracle.banking.customer.entity.CustomerKyc;
import com.oracle.banking.customer.entity.CustomerProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

public final class CustomerOperationsDtos {
    private CustomerOperationsDtos() {}

    public record CustomerItem(
            String customerId,
            String userId,
            String fullName,
            LocalDate dateOfBirth,
            String city,
            String state,
            String country,
            String postalCode,
            String profileStatus,
            String kycStatus,
            Instant createdAt,
            Instant updatedAt) {
        public static CustomerItem from(CustomerProfile profile, CustomerKyc kyc) {
            return new CustomerItem(
                    profile.getCustomerId(),
                    profile.getUserId(),
                    profile.getFullName(),
                    profile.getDateOfBirth(),
                    profile.getCity(),
                    profile.getState(),
                    profile.getCountry(),
                    profile.getPostalCode(),
                    profile.getProfileStatus(),
                    kyc == null ? "NOT_SUBMITTED" : kyc.getStatus().name(),
                    profile.getCreatedAt(),
                    profile.getUpdatedAt());
        }
    }

    public record CustomerPage(List<CustomerItem> items, int page, int size, long totalElements, int totalPages) {
        public static CustomerPage from(Page<CustomerProfile> result, List<CustomerItem> items) {
            return new CustomerPage(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record CustomerSummary(long total, long active, long kycPending, long kycVerified, long kycRejected) {}
}
