package com.oracle.banking.notification.repository;

import com.oracle.banking.notification.entity.EmailDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, String> {
}
