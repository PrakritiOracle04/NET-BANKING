package com.oracle.banking.audit.repository;

import com.oracle.banking.audit.entity.AuditConsumerFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditConsumerFailureRepository extends JpaRepository<AuditConsumerFailure, String> {}
