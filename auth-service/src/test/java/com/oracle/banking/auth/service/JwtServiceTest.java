package com.oracle.banking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void issuedTokenContainsSidAndIdentityClaims() {
        JwtService service = new JwtService(SECRET, 30);
        Role role = new Role("role-customer", "CUSTOMER");
        AppUser user = new AppUser(
                "user-1", role, "gokul", "gokul@example.com", "+919876543210", "encoded");

        IssuedToken issued = service.issue(user, "session-1");
        Claims claims = service.parse(issued.value());

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("sid", String.class)).isEqualTo("session-1");
        assertThat(claims.get("username", String.class)).isEqualTo("gokul");
        assertThat(claims.get("roles")).isEqualTo(java.util.List.of("CUSTOMER"));
        assertThat(claims.getExpiration().toInstant()).isEqualTo(issued.expiresAt());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1);
        Role role = new Role("role-customer", "CUSTOMER");
        AppUser user = new AppUser(
                "user-1", role, "gokul", "gokul@example.com", "+919876543210", "encoded");

        IssuedToken issued = service.issue(user, "session-1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.parse(issued.value()))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
