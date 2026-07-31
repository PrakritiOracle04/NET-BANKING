package com.oracle.banking.auth.controller;

import com.oracle.banking.auth.service.AuthenticationService;
import com.oracle.banking.auth.service.AuthenticationService.NotificationRecipient;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth/users")
public class InternalAuthController {
    private final AuthenticationService service; private final String key;
    public InternalAuthController(AuthenticationService service, @Value("${services.internal-api-key}") String key) { this.service = service; this.key = key; }
    @GetMapping("/{username}/notification-recipient")
    NotificationRecipient recipient(@PathVariable String username, @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
        if (!key.equals(suppliedKey)) throw new AccessDeniedException("Invalid internal API key");
        return service.notificationRecipient(username);
    }
}
