package com.oracle.banking.auth.repository;

import com.oracle.banking.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUserIdAndStatus(String userId, String status);
}
