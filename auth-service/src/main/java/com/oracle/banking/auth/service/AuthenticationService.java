package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.dto.LoginRequest;
import com.oracle.banking.auth.dto.LoginResponse;
import com.oracle.banking.auth.dto.RegisterRequest;
import com.oracle.banking.auth.dto.RegisterResponse;
import com.oracle.banking.auth.dto.UserResponse;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.Role;
import com.oracle.banking.auth.entity.SessionStatus;
import com.oracle.banking.auth.entity.UserSession;
import com.oracle.banking.auth.event.AuthNotificationEventPublisher;
import com.oracle.banking.auth.exception.BadCredentialsException;
import com.oracle.banking.auth.exception.BadRequestException;
import com.oracle.banking.auth.exception.DuplicateResourceException;
import com.oracle.banking.auth.exception.ResourceNotFoundException;
import com.oracle.banking.auth.exception.TwoFactorException;
import com.oracle.banking.auth.repository.AppUserRepository;
import com.oracle.banking.auth.repository.RoleRepository;
import com.oracle.banking.auth.repository.UserSessionRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.validation.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final CustomerClient customerClient;
    private final TwoFactorClient twoFactorClient;
    private final SessionService sessionService;
    private final AuthNotificationEventPublisher notificationEvents;

    public AuthenticationService(AppUserRepository users, RoleRepository roles, UserSessionRepository sessions,
                                 PasswordEncoder passwordEncoder, CustomerClient customerClient,
                                 TwoFactorClient twoFactorClient, SessionService sessionService,
                                 AuthNotificationEventPublisher notificationEvents) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.customerClient = customerClient;
        this.twoFactorClient = twoFactorClient;
        this.sessionService = sessionService;
        this.notificationEvents = notificationEvents;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!PasswordPolicy.isValid(request.password())) {
            throw new BadRequestException("Password must contain upper-case, lower-case, number, special character and be at least 8 characters");
        }
        if (users.existsByUsername(request.username())) throw new DuplicateResourceException("Username already exists");
        if (users.existsByEmail(request.email())) throw new DuplicateResourceException("Email already exists");
        Role customer = roles.findByRoleName(SecurityConstants.CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Customer role is not configured"));
        AppUser user = users.save(new AppUser(UUID.randomUUID().toString(), customer, request.username(),
                request.email(), request.phone(), passwordEncoder.encode(request.password())));
        customerClient.createProfile(user, request);
        notificationEvents.registrationSucceeded(user);
        log.info("Registration completed for user {}", user.getUserId());
        return new RegisterResponse(user.getUserId(), user.getUsername(), user.getEmail(), customer.getRoleName(), false);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsernameOrEmail(request.username(), request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        boolean twoFactorEnabled = twoFactorClient.isEnabled(user.getUserId());
        if (twoFactorEnabled) {
            if (request.otpCode() == null || request.otpCode().isBlank()) throw new TwoFactorException("OTP code is required");
            twoFactorClient.verify(user.getUserId(), request.otpCode());
        }
        IssuedToken token = sessionService.issue(user);
        notificationEvents.loginSucceeded(user);
        log.info("Login successful for user {}", user.getUserId());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), user.getUsername(),
                user.getRole().getRoleName(), twoFactorEnabled);
    }

    @Transactional
    public void logout(String userId) {
        sessions.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).forEach(UserSession::invalidate);
        log.info("Logout completed for user {}", userId);
    }

    public UserResponse currentUser(String userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new UserResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getRole().getRoleName());
    }

    public NotificationRecipient notificationRecipient(String userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new NotificationRecipient(user.getUserId(), user.getUsername(), user.getEmail());
    }

    public record NotificationRecipient(String userId, String username, String email) {}
}
