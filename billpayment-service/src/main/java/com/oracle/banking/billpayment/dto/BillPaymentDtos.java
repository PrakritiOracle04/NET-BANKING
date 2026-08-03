package com.oracle.banking.billpayment.dto;

import com.oracle.banking.billpayment.entity.BillPayment;
import com.oracle.banking.billpayment.entity.BillPaymentStatus;
import com.oracle.banking.billpayment.entity.BillerCatalog;
import com.oracle.banking.billpayment.entity.BillerCategory;
import com.oracle.banking.billpayment.entity.BillerStatus;
import com.oracle.banking.billpayment.entity.CustomerBiller;
import com.oracle.banking.billpayment.entity.CustomerBillerStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class BillPaymentDtos {
    private BillPaymentDtos() {}

    public record BillerCatalogRequest(
            @NotBlank @Size(max = 40) String billerCode,
            @NotBlank @Size(max = 120) String billerName,
            @NotNull BillerCategory category,
            @NotNull BillerStatus status
    ) {}

    public record BillerCatalogResponse(
            String billerId,
            String billerCode,
            String billerName,
            BillerCategory category,
            BillerStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static BillerCatalogResponse from(BillerCatalog biller) {
            return new BillerCatalogResponse(
                    biller.getBillerId(), biller.getBillerCode(), biller.getBillerName(),
                    biller.getCategory(), biller.getStatus(), biller.getCreatedAt(), biller.getUpdatedAt());
        }
    }

    public record CustomerBillerRequest(
            @NotBlank @Size(max = 36) String billerId,
            @NotBlank @Size(max = 80) String consumerReference,
            @NotBlank @Size(max = 80) String nickname
    ) {}

    public record CustomerBillerResponse(
            String customerBillerId,
            String customerUserId,
            BillerCatalogResponse biller,
            String consumerReference,
            String nickname,
            CustomerBillerStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CustomerBillerResponse from(CustomerBiller registration) {
            return new CustomerBillerResponse(
                    registration.getCustomerBillerId(), registration.getCustomerUserId(),
                    BillerCatalogResponse.from(registration.getBiller()), registration.getConsumerReference(),
                    registration.getNickname(), registration.getStatus(), registration.getCreatedAt(),
                    registration.getUpdatedAt());
        }
    }

    public record BillPaymentResponse(
            String billPaymentId,
            String customerUserId,
            String customerBillerId,
            String billerId,
            String billerName,
            String consumerReference,
            String sourceAccountId,
            BigDecimal amount,
            BillPaymentStatus status,
            String workflowReference,
            String transactionId,
            String transactionReference,
            String description,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        public static BillPaymentResponse from(BillPayment payment) {
            return new BillPaymentResponse(
                    payment.getBillPaymentId(), payment.getCustomerUserId(),
                    payment.getCustomerBiller().getCustomerBillerId(), payment.getBillerId(),
                    payment.getBillerName(), payment.getConsumerReference(), payment.getSourceAccountId(),
                    payment.getAmount(), payment.getStatus(), payment.getWorkflowReference(),
                    payment.getTransactionId(), payment.getTransactionReference(), payment.getDescription(),
                    payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt(),
                    payment.getCompletedAt());
        }
    }

    public record InternalBillerValidationResponse(
            String customerBillerId,
            String customerUserId,
            String billerId,
            String billerName,
            String consumerReference,
            boolean active
    ) {}

    public record InternalCreateBillPaymentRequest(
            @NotBlank @Size(max = 36) String customerUserId,
            @NotBlank @Size(max = 36) String customerBillerId,
            @NotBlank @Size(max = 36) String sourceAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 80) String workflowReference,
            @Size(max = 160) String description
    ) {}

    public record InternalCompleteBillPaymentRequest(
            @NotBlank @Size(max = 36) String transactionId,
            @NotBlank @Size(max = 80) String transactionReference
    ) {}

    public record InternalFailBillPaymentRequest(@NotBlank @Size(max = 500) String reason) {}
}
