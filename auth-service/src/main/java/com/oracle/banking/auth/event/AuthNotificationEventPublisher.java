package com.oracle.banking.auth.event;

import com.oracle.banking.auth.entity.AppUser;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AuthNotificationEventPublisher {
    private final ApplicationEventPublisher events;

    public AuthNotificationEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void registrationSucceeded(AppUser user) {
        events.publishEvent(new AuthNotificationEvent(
                "registration-success",
                "REG-" + user.getUserId(),
                user.getEmail(),
                "WELCOME",
                Map.of("customerName", user.getUsername())));
    }

    public void loginSucceeded(AppUser user) {
        events.publishEvent(new AuthNotificationEvent(
                "login-alert",
                "LOGIN-" + UUID.randomUUID(),
                user.getEmail(),
                "LOGIN_ALERT",
                Map.of(
                        "customerName", user.getUsername(),
                        "currentTime", Instant.now().toString())));
    }
}
