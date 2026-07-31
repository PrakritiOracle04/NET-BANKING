package com.oracle.banking.notification.repository;
import com.oracle.banking.notification.NotificationStatus; import com.oracle.banking.notification.entity.EmailNotification; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailNotificationRepository extends JpaRepository<EmailNotification,String>{ List<EmailNotification> findByStatusOrderByCreatedAtDesc(NotificationStatus status); List<EmailNotification> findAllByOrderByCreatedAtDesc(); }
