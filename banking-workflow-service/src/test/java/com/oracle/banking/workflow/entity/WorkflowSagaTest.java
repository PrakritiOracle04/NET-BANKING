package com.oracle.banking.workflow.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WorkflowSagaTest {
    @Test
    void pendingBillCreationIsTreatedAsCompensatableMutation() {
        WorkflowSaga saga = new WorkflowSaga(
                "user-id",
                "idempotency-key",
                WorkflowType.BILL_PAYMENT,
                "BIL-reference",
                "account-id",
                null,
                BigDecimal.TEN,
                "test bill");

        saga.billPaymentRequested("customer-biller-id");
        assertThat(saga.hasMutation()).isFalse();

        saga.prerequisitesValidated();
        assertThat(saga.hasMutation()).isTrue();
    }

    @Test
    void accountOpeningSagaRetainsTheInitialDepositForIdempotency() {
        BigDecimal initialDeposit = new BigDecimal("25000.00");
        WorkflowSaga saga = new WorkflowSaga(
                "user-id",
                "idempotency-key",
                WorkflowType.ACCOUNT_OPENING,
                "AOP-reference",
                null,
                null,
                initialDeposit,
                "Account opening");

        saga.accountOpeningRequested("SAVINGS", "ORCL0000001");

        assertThat(saga.getAmount()).isEqualByComparingTo(initialDeposit);
        assertThat(saga.getAccountType()).isEqualTo("SAVINGS");
        assertThat(saga.getBranchIfsc()).isEqualTo("ORCL0000001");
    }
}
