package com.oracle.banking.account.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

class AccountTest {
    @Test
    void balanceColumnsUseDatabaseGeneratedOpeningBalance() throws NoSuchFieldException {
        Account account = new Account();

        account.beforeCreate();

        assertThat(account.getAvailableBalance()).isNull();
        assertThat(account.getLedgerBalance()).isNull();
        assertDatabaseGeneratedDefault("availableBalance");
        assertDatabaseGeneratedDefault("ledgerBalance");
    }

    private void assertDatabaseGeneratedDefault(String fieldName) throws NoSuchFieldException {
        Field field = Account.class.getDeclaredField(fieldName);
        assertThat(field.getAnnotation(ColumnDefault.class).value()).isEqualTo("100000");
        assertThat(field.getAnnotation(Generated.class).event()).containsExactly(EventType.INSERT);
    }
}
