package com.oracle.banking.billpayment.dto;

import com.oracle.banking.billpayment.entity.BillPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class BillPaymentOperationsDtos {
    private BillPaymentOperationsDtos() {}

    public record BillPaymentItem(
            String billPaymentId, String customerUserId, String billerId, String billerName,
            String sourceAccountId, BigDecimal amount, String status, String workflowReference,
            String transactionReference, String failureReason, Instant createdAt, Instant completedAt) {
        public static BillPaymentItem from(BillPayment payment) {
            return new BillPaymentItem(
                    payment.getBillPaymentId(), payment.getCustomerUserId(), payment.getBillerId(),
                    payment.getBillerName(), payment.getSourceAccountId(), payment.getAmount(),
                    payment.getStatus().name(), payment.getWorkflowReference(), payment.getTransactionReference(),
                    payment.getFailureReason(), payment.getCreatedAt(), payment.getCompletedAt());
        }
    }

    public record BillPaymentPage(List<BillPaymentItem> items, int page, int size, long totalElements, int totalPages) {
        public static BillPaymentPage from(Page<BillPayment> result) {
            return new BillPaymentPage(result.getContent().stream().map(BillPaymentItem::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        }
    }

    public record BillPaymentSummary(long total, long pending, long successful, long failed, long cancelled) {}
}
