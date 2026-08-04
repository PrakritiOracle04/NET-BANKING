package com.oracle.banking.scheduler.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemScheduleSeeder implements CommandLineRunner {
    private final BankingSchedulerService service;

    public SystemScheduleSeeder(BankingSchedulerService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        service.seedSystemSchedules();
    }
}
