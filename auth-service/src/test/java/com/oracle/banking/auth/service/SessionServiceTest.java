package com.oracle.banking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.dto.SessionValidationResponse;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.Role;
import com.oracle.banking.auth.entity.SessionStatus;
import com.oracle.banking.auth.entity.UserSession;
import com.oracle.banking.auth.exception.SessionAuthenticationException;
import com.oracle.banking.auth.repository.AppUserRepository;
import com.oracle.banking.auth.repository.UserSessionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SessionServiceTest {
    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Mock
    private UserSessionRepository sessions;
    @Mock
    private AppUserRepository users;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;

    private JwtService jwtService;
    private SessionService service;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtService = new JwtService(SECRET, 30);
        service = new SessionService(sessions, users, jwtService);
        customer = userWithRole("CUSTOMER");
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issuePersistsSessionMatchingSidAndExpiry() {
        IssuedToken token = service.issue(customer);
        org.mockito.Mockito.verify(sessions).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();

        assertThat(jwtService.parse(token.value()).get("sid", String.class)).isEqualTo(saved.getSessionId());
        assertThat(saved.getUserId()).isEqualTo(customer.getUserId());
        assertThat(saved.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void validatesActiveSessionAndCurrentRole() {
        IssuedToken token = service.issue(customer);
        org.mockito.Mockito.verify(sessions).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        when(sessions.findById(saved.getSessionId())).thenReturn(Optional.of(saved));
        when(users.findById(customer.getUserId())).thenReturn(Optional.of(customer));

        SessionValidationResponse response = service.validate("Bearer " + token.value());

        assertThat(response.valid()).isTrue();
        assertThat(response.sessionId()).isEqualTo(saved.getSessionId());
        assertThat(response.userId()).isEqualTo(customer.getUserId());
        assertThat(response.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void rejectsInvalidatedSession() {
        IssuedToken token = service.issue(customer);
        org.mockito.Mockito.verify(sessions).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        saved.invalidate();
        when(sessions.findById(saved.getSessionId())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.validate("Bearer " + token.value()))
                .isInstanceOf(SessionAuthenticationException.class);
    }

    @Test
    void rejectsExpiredDatabaseSessionEvenWhenJwtIsValid() {
        IssuedToken token = jwtService.issue(customer, "expired-session");
        UserSession expired = new UserSession("expired-session", customer.getUserId(), Instant.now().minusSeconds(1));
        when(sessions.findById("expired-session")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validate("Bearer " + token.value()))
                .isInstanceOf(SessionAuthenticationException.class);
    }

    @Test
    void rejectsTokenWhenCurrentRoleChanged() {
        IssuedToken token = service.issue(customer);
        org.mockito.Mockito.verify(sessions).save(sessionCaptor.capture());
        UserSession saved = sessionCaptor.getValue();
        when(sessions.findById(saved.getSessionId())).thenReturn(Optional.of(saved));
        when(users.findById(customer.getUserId())).thenReturn(Optional.of(userWithRole("ADMIN")));

        assertThatThrownBy(() -> service.validate("Bearer " + token.value()))
                .isInstanceOf(SessionAuthenticationException.class);
    }

    private AppUser userWithRole(String roleName) {
        return new AppUser(
                "user-1", new Role("role-" + roleName.toLowerCase(), roleName),
                "gokul", "gokul@example.com", "+919876543210", "encoded");
    }
}
