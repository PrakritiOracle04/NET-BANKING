package com.oracle.banking.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankingSchedulerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingSchedulerServiceApplication.class, args);
    }
}
