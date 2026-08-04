package com.oracle.banking.auth.service;

import com.oracle.banking.auth.dto.IssuedToken;
import com.oracle.banking.auth.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    private final String secret;
    private final long expirationMinutes;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-minutes:30}") long expirationMinutes) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    public IssuedToken issue(AppUser user, String sessionId) {
        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                .plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(user.getUserId())
                .claim("sid", sessionId)
                .claim("username", user.getUsername())
                .claim("roles", List.of(user.getRole().getRoleName()))
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(key())
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
