package com.oracle.banking.workflow.config;

import com.oracle.banking.workflow.entity.WorkflowStatus;
import com.oracle.banking.workflow.entity.WorkflowType;
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
public class WorkflowSchemaReconciler implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public WorkflowSchemaReconciler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconcileEnumConstraint(
                "BANKING_WORKFLOWS",
                "WORKFLOW_TYPE",
                "CK_BANKING_WORKFLOW_TYPE",
                enumValues(WorkflowType.values()));
        reconcileEnumConstraint(
                "BANKING_WORKFLOWS",
                "STATUS",
                "CK_BANKING_WORKFLOW_STATUS",
                enumValues(WorkflowStatus.values()));
    }

    private void reconcileEnumConstraint(
            String table,
            String column,
            String constraintName,
            List<String> allowedValues) {
        List<CheckConstraint> matching = jdbc.query(
                "select constraint_name, search_condition_vc from user_constraints "
                        + "where table_name = ? and constraint_type = 'C'",
                (result, row) -> new CheckConstraint(
                        result.getString("constraint_name"),
                        result.getString("search_condition_vc")),
                table).stream()
                .filter(constraint -> isEnumConstraint(constraint.condition(), column))
                .toList();

        boolean current = !matching.isEmpty() && matching.stream()
                .map(CheckConstraint::condition)
                .allMatch(condition -> containsAll(condition, allowedValues));
        if (current) return;

        matching.forEach(constraint -> jdbc.execute(
                "alter table " + table + " drop constraint " + constraint.name()));
        jdbc.execute("alter table " + table + " add constraint " + constraintName
                + " check (" + column + " in (" + quoted(allowedValues) + "))");
    }

    private boolean isEnumConstraint(String condition, String column) {
        if (condition == null) return false;
        String normalized = condition.toUpperCase(Locale.ROOT).replace("\"", "");
        return normalized.contains(column) && normalized.contains(" IN (");
    }

    private boolean containsAll(String condition, List<String> values) {
        String normalized = condition == null ? "" : condition.toUpperCase(Locale.ROOT);
        return values.stream().allMatch(value -> normalized.contains("'" + value + "'"));
    }

    private List<String> enumValues(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private String quoted(List<String> values) {
        return values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", "));
    }

    private record CheckConstraint(String name, String condition) {}
}
