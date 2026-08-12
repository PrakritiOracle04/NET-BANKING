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
}
