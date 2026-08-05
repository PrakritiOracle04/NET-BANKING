package com.oracle.banking.scheduler.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SchedulerSchemaReconciler implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SchedulerSchemaReconciler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn(
                "BANKING_SCHEDULES",
                "SYSTEM_KEY",
                "alter table BANKING_SCHEDULES add SYSTEM_KEY varchar2(30 char)");
        ensureConstraint(
                "UK_SYSTEM_SCHEDULE_KEY",
                "alter table BANKING_SCHEDULES add constraint UK_SYSTEM_SCHEDULE_KEY unique (SYSTEM_KEY)");
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbc.queryForObject(
                "select count(*) from user_tab_columns where table_name = ? and column_name = ?",
                Integer.class,
                table,
                column);
        if (count == null || count == 0) jdbc.execute(ddl);
    }

    private void ensureConstraint(String name, String ddl) {
        Integer count = jdbc.queryForObject(
                "select count(*) from user_constraints where constraint_name = ?",
                Integer.class,
                name);
        if (count == null || count == 0) jdbc.execute(ddl);
    }
}
