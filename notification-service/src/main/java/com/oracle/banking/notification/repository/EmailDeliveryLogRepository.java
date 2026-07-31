package com.oracle.banking.notification.repository;

import com.oracle.banking.notification.entity.EmailDeliveryLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, String> {
    List<EmailDeliveryLog> findByNotificationIdOrderByAttemptAsc(String notificationId);
}
