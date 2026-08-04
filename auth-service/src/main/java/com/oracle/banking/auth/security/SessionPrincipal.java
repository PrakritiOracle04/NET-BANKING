package com.oracle.banking.auth.security;

import java.security.Principal;

public record SessionPrincipal(String userId, String sessionId) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
