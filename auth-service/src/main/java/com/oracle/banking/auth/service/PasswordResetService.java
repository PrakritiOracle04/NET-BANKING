package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetConfirmRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetVerifyRequest;
import com.oracle.banking.auth.dto.PasswordResetDtos.PasswordResetVerifyResponse;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.PasswordResetChallenge;
import com.oracle.banking.auth.entity.PasswordResetStatus;
import com.oracle.banking.auth.event.AuthAuditPublisher;
import com.oracle.banking.auth.exception.BadRequestException;
import com.oracle.banking.auth.repository.AppUserRepository;
import com.oracle.banking.auth.repository.PasswordResetChallengeRepository;
import com.oracle.banking.shared.validation.PasswordPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final String GENERIC_REQUEST_MESSAGE = "If the email is registered, a password reset code will be sent";
    private static final String INVALID_CODE_MESSAGE = "Invalid or expired password reset code";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired password reset token";

    private final AppUserRepository users;
    private final PasswordResetChallengeRepository challenges;
    private final PasswordResetTokenService tokenService;
    private final PasswordResetNotificationClient notifications;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final AuthAuditPublisher audit;
    private final Duration otpTtl;
    private final Duration tokenTtl;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final int retentionDays;

    public PasswordResetService(
            AppUserRepository users,
            PasswordResetChallengeRepository challenges,
            PasswordResetTokenService tokenService,
            PasswordResetNotificationClient notifications,
            PasswordEncoder passwordEncoder,
            SessionService sessions,
            AuthAuditPublisher audit,
            @Value("${password-reset.otp-ttl-seconds}") long otpTtlSeconds,
            @Value("${password-reset.token-ttl-seconds}") long tokenTtlSeconds,
            @Value("${password-reset.max-attempts}") int maxAttempts,
            @Value("${password-reset.resend-cooldown-seconds}") long resendCooldownSeconds,
            @Value("${password-reset.retention-days}") int retentionDays) {
        this.users = users;
        this.challenges = challenges;
        this.tokenService = tokenService;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
        this.otpTtl = Duration.ofSeconds(otpTtlSeconds);
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
        this.maxAttempts = maxAttempts;
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.retentionDays = retentionDays;
    }

    @Transactional
    public String request(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return GENERIC_REQUEST_MESSAGE;
        }

        PasswordResetChallenge previous = challenges
                .findFirstByUserUserIdAndStatusOrderByCreatedAtDesc(user.getUserId(), PasswordResetStatus.PENDING)
                .orElse(null);
        Instant now = Instant.now();
        if (previous != null && previous.getLastSentAt().plus(resendCooldown).isAfter(now)) {
            return GENERIC_REQUEST_MESSAGE;
        }

        challenges.updateStatusForUserAndStatuses(
                user.getUserId(),
                Set.of(PasswordResetStatus.PENDING, PasswordResetStatus.VERIFIED),
                PasswordResetStatus.EXPIRED);

        String otp = tokenService.otp();
        PasswordResetChallenge challenge = challenges.save(new PasswordResetChallenge(
                user,
                tokenService.digest(otp),
                now.plus(otpTtl),
                now));

        try {
            notifications.sendTemplate(
                    user.getEmail(),
                    "PASSWORD_RESET_OTP",
                    Map.of(
                            "customerName", user.getUsername(),
                            "otpCode", otp,
                            "expiresInMinutes", String.valueOf(otpTtl.toMinutes())),
                    "password-reset-requested",
                    challenge.getChallengeId());
            audit.passwordResetRequested(user.getUserId());
        } catch (RuntimeException exception) {
            challenge.deliveryFailed();
        }
        return GENERIC_REQUEST_MESSAGE;
    }

    @Transactional
    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw invalidCode(null, "NO_ACTIVE_CHALLENGE");
        }
        PasswordResetChallenge challenge = latestPending(user.getUserId());
        if (challenge == null || challenge.getOtpExpiresAt().isBefore(Instant.now())) {
            if (challenge != null) challenge.expire();
            throw invalidCode(user.getUserId(), "EXPIRED_OR_MISSING");
        }
        if (!tokenService.matches(request.otpCode(), challenge.getOtpDigest())) {
            challenge.failedAttempt(maxAttempts);
            throw invalidCode(user.getUserId(), "INVALID_OTP");
        }

        String token = tokenService.resetToken();
        Instant expiresAt = Instant.now().plus(tokenTtl);
        challenge.verify(tokenService.digest(token), expiresAt);
        audit.passwordResetVerified(user.getUserId());
        return new PasswordResetVerifyResponse(token, expiresAt);
    }

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match");
        }
        if (!PasswordPolicy.isValid(request.newPassword())) {
            throw new BadRequestException("Password must contain upper-case, lower-case, number, special character and be at least 8 characters");
        }

        PasswordResetChallenge challenge = challenges.findByResetTokenDigest(tokenService.digest(request.resetToken()))
                .orElseThrow(() -> new BadRequestException(INVALID_TOKEN_MESSAGE));
        if (challenge.getStatus() != PasswordResetStatus.VERIFIED
                || challenge.getTokenExpiresAt() == null
                || challenge.getTokenExpiresAt().isBefore(Instant.now())) {
            if (challenge.getStatus() == PasswordResetStatus.VERIFIED) challenge.expire();
            throw new BadRequestException(INVALID_TOKEN_MESSAGE);
        }

        AppUser user = challenge.getUser();
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        challenge.consume();
        int invalidated = sessions.invalidateAll(user.getUserId());
        audit.passwordResetCompleted(user.getUserId(), invalidated);
        try {
            notifications.sendTemplate(
                    user.getEmail(),
                    "PASSWORD_CHANGED",
                    Map.of("customerName", user.getUsername(), "changedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()),
                    "password-reset-completed",
                    challenge.getChallengeId());
        } catch (RuntimeException ignored) {
            // Password reset is already durably complete. Do not roll it back because a follow-up email failed.
        }
    }

    @Scheduled(fixedDelayString = "${password-reset.cleanup-delay-ms}")
    @Transactional
    public void cleanupExpiredChallenges() {
        challenges.deleteCreatedBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }

    private PasswordResetChallenge latestPending(String userId) {
        return challenges.lockByUserAndStatus(userId, PasswordResetStatus.PENDING)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private BadRequestException invalidCode(String userId, String reasonCode) {
        if (userId != null) audit.passwordResetVerificationFailed(userId, reasonCode);
        return new BadRequestException(INVALID_CODE_MESSAGE);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }
}
