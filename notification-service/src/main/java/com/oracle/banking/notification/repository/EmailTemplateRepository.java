package com.oracle.banking.notification.repository;
import com.oracle.banking.notification.entity.EmailTemplate; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate,String>{ Optional<EmailTemplate> findByNameAndActiveTrue(String name); }
