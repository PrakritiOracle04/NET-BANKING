package com.oracle.banking.auth.repository;

import com.oracle.banking.auth.entity.UserSession;
import com.oracle.banking.auth.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUserIdAndStatus(String userId, SessionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserSession session set session.status = :expired "
            + "where session.status = :active and session.expiresAt <= :now")
    int expireDueSessions(
            @Param("active") SessionStatus active,
            @Param("expired") SessionStatus expired,
            @Param("now") Instant now);
}
