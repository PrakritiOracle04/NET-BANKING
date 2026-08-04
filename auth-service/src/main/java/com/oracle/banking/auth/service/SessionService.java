package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.dto.SessionValidationResponse;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.SessionStatus;
import com.oracle.banking.auth.entity.UserSession;
import com.oracle.banking.auth.exception.SessionAuthenticationException;
import com.oracle.banking.auth.repository.AppUserRepository;
import com.oracle.banking.auth.repository.UserSessionRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private static final String INVALID_SESSION_MESSAGE = "Session is invalid or expired";

    private final UserSessionRepository sessions;
    private final AppUserRepository users;
    private final JwtService jwtService;

    public SessionService(UserSessionRepository sessions, AppUserRepository users, JwtService jwtService) {
        this.sessions = sessions;
        this.users = users;
        this.jwtService = jwtService;
    }

    @Transactional
    public IssuedToken issue(AppUser user) {
        String sessionId = UUID.randomUUID().toString();
        IssuedToken token = jwtService.issue(user, sessionId);
        sessions.save(new UserSession(sessionId, user.getUserId(), token.expiresAt()));
        return token;
    }

    @Transactional(readOnly = true)
    public SessionValidationResponse validate(String authorizationHeader) {
        Claims claims;
        try {
            claims = jwtService.parse(bearerToken(authorizationHeader));
        } catch (RuntimeException exception) {
            throw invalidSession();
        }

        String sessionId = claims.get("sid", String.class);
        String userId = claims.getSubject();
        if (sessionId == null || sessionId.isBlank() || userId == null || userId.isBlank()) {
            throw invalidSession();
        }

        UserSession session = sessions.findById(sessionId).orElseThrow(this::invalidSession);
        if (!userId.equals(session.getUserId())
                || session.getStatus() != SessionStatus.ACTIVE
                || !session.getExpiresAt().isAfter(Instant.now())) {
            throw invalidSession();
        }

        AppUser user = users.findById(userId).orElseThrow(this::invalidSession);
        if (!"ACTIVE".equals(user.getStatus())) throw invalidSession();

        List<String> tokenRoles = roles(claims);
        String currentRole = user.getRole().getRoleName();
        if (tokenRoles.size() != 1 || !tokenRoles.contains(currentRole)) throw invalidSession();

        return new SessionValidationResponse(true, sessionId, userId, List.of(currentRole), session.getExpiresAt());
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            throw invalidSession();
        }
        String token = authorizationHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        if (token.isBlank()) throw invalidSession();
        return token;
    }

    private List<String> roles(Claims claims) {
        Object claim = claims.get("roles");
        if (!(claim instanceof Collection<?> values)) throw invalidSession();
        return values.stream().map(String::valueOf).toList();
    }

    private SessionAuthenticationException invalidSession() {
        return new SessionAuthenticationException(INVALID_SESSION_MESSAGE);
    }
}
