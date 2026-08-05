package com.oracle.banking.auth.controller;

import com.oracle.banking.auth.dto.SessionValidationResponse;
import com.oracle.banking.auth.service.SessionService;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth/sessions")
public class InternalSessionController {
    private final SessionService sessions;
    private final String internalApiKey;

    public InternalSessionController(
            SessionService sessions,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.sessions = sessions;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/validate")
    SessionValidationResponse validate(
            @RequestHeader(value = SecurityConstants.INTERNAL_API_KEY_HEADER, required = false) String suppliedKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (!internalApiKey.equals(suppliedKey)) throw new AccessDeniedException("Invalid internal API key");
        return sessions.validate(authorizationHeader);
    }
}
