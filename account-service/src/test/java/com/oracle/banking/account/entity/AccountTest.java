package com.oracle.banking.account.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {
    @Test
    void newAccountPreservesTheRequestedInitialDepositAsBothBalances() {
        Account account = new Account();
        BigDecimal initialDeposit = new BigDecimal("25000.00");
        account.setInitialDeposit(initialDeposit);
        account.setAvailableBalance(initialDeposit);
        account.setLedgerBalance(initialDeposit);

        account.beforeCreate();

        assertThat(account.getInitialDeposit()).isEqualByComparingTo(initialDeposit);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(initialDeposit);
        assertThat(account.getLedgerBalance()).isEqualByComparingTo(initialDeposit);
    }
}
