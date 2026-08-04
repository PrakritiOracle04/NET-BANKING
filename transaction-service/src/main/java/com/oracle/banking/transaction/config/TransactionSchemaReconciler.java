package com.oracle.banking.transaction.config;

import com.oracle.banking.transaction.entity.TransactionType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class TransactionSchemaReconciler implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public TransactionSchemaReconciler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = Arrays.stream(TransactionType.values()).map(Enum::name).toList();
        List<CheckConstraint> matching = jdbc.query(
                "select constraint_name, search_condition_vc from user_constraints "
                        + "where table_name = 'BANK_TRANSACTIONS' and constraint_type = 'C'",
                (result, row) -> new CheckConstraint(
                        result.getString("constraint_name"),
                        result.getString("search_condition_vc"))).stream()
                .filter(constraint -> isTypeConstraint(constraint.condition()))
                .toList();

        boolean current = !matching.isEmpty() && matching.stream()
                .map(CheckConstraint::condition)
                .allMatch(condition -> containsAll(condition, values));
        if (current) return;

        matching.forEach(constraint -> jdbc.execute(
                "alter table BANK_TRANSACTIONS drop constraint " + constraint.name()));
        String quoted = values.stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table BANK_TRANSACTIONS add constraint CK_BANK_TRANSACTION_TYPE "
                + "check (TRANSACTION_TYPE in (" + quoted + "))");
    }

    private boolean isTypeConstraint(String condition) {
        if (condition == null) return false;
        String normalized = condition.toUpperCase(Locale.ROOT).replace("\"", "");
        return normalized.contains("TRANSACTION_TYPE") && normalized.contains(" IN (");
    }

    private boolean containsAll(String condition, List<String> values) {
        String normalized = condition == null ? "" : condition.toUpperCase(Locale.ROOT);
        return values.stream().allMatch(value -> normalized.contains("'" + value + "'"));
    }

    private record CheckConstraint(String name, String condition) {}
}
