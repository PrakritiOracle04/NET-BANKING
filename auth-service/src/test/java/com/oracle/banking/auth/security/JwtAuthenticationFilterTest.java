package com.oracle.banking.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.banking.auth.TestSecrets;
import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.Role;
import com.oracle.banking.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {
    private static final String SECRET = TestSecrets.randomBase64Key();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsSessionPrincipalFromSidClaim() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 30);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        AppUser user = new AppUser(
                "user-1", new Role("role-1", "CUSTOMER"), "gokul",
                "gokul@example.com", "+919876543210", "encoded");
        IssuedToken token = jwtService.issue(user, "session-1");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token.value());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new SessionPrincipal("user-1", "session-1"));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user-1");
        verify(chain).doFilter(request, response);
    }
}
