package com.oracle.banking.auth.repository;

import com.oracle.banking.auth.entity.PasswordResetChallenge;
import com.oracle.banking.auth.entity.PasswordResetStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, String> {
    Optional<PasswordResetChallenge> findFirstByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId, PasswordResetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from PasswordResetChallenge challenge "
            + "where challenge.user.userId = :userId and challenge.status = :status "
            + "order by challenge.createdAt desc")
    List<PasswordResetChallenge> lockByUserAndStatus(
            @Param("userId") String userId,
            @Param("status") PasswordResetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallenge> findByResetTokenDigest(String resetTokenDigest);

    @Modifying
    @Query("update PasswordResetChallenge challenge set challenge.status = :target "
            + "where challenge.user.userId = :userId and challenge.status in :statuses")
    int updateStatusForUserAndStatuses(
            @Param("userId") String userId,
            @Param("statuses") Collection<PasswordResetStatus> statuses,
            @Param("target") PasswordResetStatus target);

    @Modifying
    @Query("delete from PasswordResetChallenge challenge where challenge.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
