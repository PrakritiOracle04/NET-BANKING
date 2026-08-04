package com.oracle.banking.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(SessionExpiryScheduler.class);

    private final SessionService sessions;

    public SessionExpiryScheduler(SessionService sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "${security.sessions.cleanup-delay-ms:60000}")
    public void expireDueSessions() {
        int expired = sessions.expireDueSessions();
        if (expired > 0) log.info("Marked {} authentication sessions as expired", expired);
    }
}
