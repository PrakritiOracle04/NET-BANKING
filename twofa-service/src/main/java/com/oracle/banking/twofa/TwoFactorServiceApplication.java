package com.oracle.banking.twofa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.twofa.entity.AuthFactor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication
@EntityScan(basePackageClasses = TwoFactorServiceApplication.class)
@EnableJpaRepositories(basePackageClasses = TwoFactorServiceApplication.class, considerNestedRepositories = true)
@OpenAPIDefinition(info = @Info(title = "Two-Factor Authentication Service", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class TwoFactorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TwoFactorServiceApplication.class, args);
    }

    public interface AuthFactorRepository extends JpaRepository<AuthFactor, String> {
        Optional<AuthFactor> findByUserId(String userId);
    }

    public record TwoFactorSetupResponse(String secret, String otpauthUri, String qrCodeBase64, String issuer,
                                         String accountName, boolean enabled) {
    }

    public record TwoFactorVerifyRequest(@NotBlank String otpCode) {
    }

    public record TwoFactorDisableRequest(@NotBlank String otpCode) {
    }

    public record TwoFactorStatusResponse(boolean enabled) {
    }

    public record InternalOtpVerification(@NotBlank String userId, @NotBlank String otpCode) {
    }

    @Component
    public static class SecretProtector {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final SecretKey key;

        SecretProtector(@Value("${twofa.encryption-key}") String encodedKey) {
            this.key = new SecretKeySpec(Decoders.BASE64.decode(encodedKey), "AES");
        }

        String encrypt(String value) {
            try {
                byte[] iv = new byte[12];
                RANDOM.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                return Base64.getEncoder().encodeToString(combined);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to protect TOTP secret", ex);
            }
        }

        String decrypt(String value) {
            try {
                byte[] combined = Base64.getDecoder().decode(value);
                byte[] iv = Arrays.copyOfRange(combined, 0, 12);
                byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to read TOTP secret", ex);
            }
        }
    }

    @Component
    public static class Totp {
        private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        private static final SecureRandom RANDOM = new SecureRandom();

        String createSecret() {
            byte[] bytes = new byte[20];
            RANDOM.nextBytes(bytes);
            return encode(bytes);
        }

        boolean valid(String secret, String code) {
            if (code == null || !code.matches("\\d{6}")) return false;
            long counter = Instant.now().getEpochSecond() / 30;
            for (long offset = -1; offset <= 1; offset++) {
                if (code.equals(code(secret, counter + offset))) return true;
            }
            return false;
        }

        private String code(String secret, long counter) {
            try {
                byte[] counterBytes = new byte[8];
                for (int index = 7; index >= 0; index--) {
                    counterBytes[index] = (byte) counter;
                    counter >>>= 8;
                }
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
                byte[] hash = mac.doFinal(counterBytes);
                int offset = hash[hash.length - 1] & 0x0f;
                int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                        | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
                return String.format("%06d", binary % 1_000_000);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to calculate TOTP", ex);
            }
        }

        private static String encode(byte[] data) {
            StringBuilder output = new StringBuilder();
            int buffer = 0, bits = 0;
            for (byte value : data) {
                buffer = (buffer << 8) | (value & 0xff);
                bits += 8;
                while (bits >= 5) {
                    output.append(BASE32[(buffer >> (bits - 5)) & 31]);
                    bits -= 5;
                }
            }
            if (bits > 0) output.append(BASE32[(buffer << (5 - bits)) & 31]);
            return output.toString();
        }

        private static byte[] decode(String value) {
            int buffer = 0, bits = 0;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (char character : value.replace("=", "").toUpperCase().toCharArray()) {
                int current = new String(BASE32).indexOf(character);
                if (current < 0) throw new IllegalArgumentException("Invalid Base32 secret");
                buffer = (buffer << 5) | current;
                bits += 5;
                if (bits >= 8) {
                    output.write((buffer >> (bits - 8)) & 0xff);
                    bits -= 8;
                }
            }
            return output.toByteArray();
        }
    }

    @Component
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {
        private final SecretKey key;

        JwtAuthenticationFilter(@Value("${security.jwt.secret}") String secret) {
            this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    Claims claims = Jwts.parser().verifyWith(key).build()
                            .parseSignedClaims(header.substring(SecurityConstants.BEARER_PREFIX.length())).getPayload();
                    @SuppressWarnings("unchecked")
                    Collection<String> roles = claims.get("roles", List.class);
                    var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
            chain.doFilter(request, response);
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    public static class SecurityConfiguration {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/internal/**").permitAll()
                            .anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Service
    public static class TwoFactorService {
        private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
        private final AuthFactorRepository factors;
        private final SecretProtector protector;
        private final Totp totp;
        private final String issuer;

        TwoFactorService(AuthFactorRepository factors, SecretProtector protector, Totp totp,
                         @Value("${twofa.issuer:Oracle Internet Banking}") String issuer) {
            this.factors = factors;
            this.protector = protector;
            this.totp = totp;
            this.issuer = issuer;
        }

        @Transactional
        TwoFactorSetupResponse setup(String userId) {
            log.info("2FA setup started for user {}", userId);
            String secret = totp.createSecret();
            AuthFactor factor = factors.findByUserId(userId).orElseGet(() -> new AuthFactor(userId, protector.encrypt(secret)));
            if (factors.findByUserId(userId).isPresent()) {
                factor.replaceSecret(protector.encrypt(secret));
                factor.disable();
            }
            factors.save(factor);
            String account = userId;
            String uri = "otpauth://totp/" + encode(issuer) + ":" + encode(account)
                    + "?secret=" + secret + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
            return new TwoFactorSetupResponse(secret, uri, qr(uri), issuer, account, false);
        }

        @Transactional
        TwoFactorStatusResponse verifySetup(String userId, String code) {
            AuthFactor factor = required(userId);
            if (!totp.valid(protector.decrypt(factor.encryptedSecret()), code)) throw new TwoFactorException("Invalid OTP code");
            factor.enable();
            log.info("2FA setup completed for user {}", userId);
            return new TwoFactorStatusResponse(true);
        }

        TwoFactorStatusResponse verify(String userId, String code) {
            AuthFactor factor = required(userId);
            if (!factor.isEnabled() || !totp.valid(protector.decrypt(factor.encryptedSecret()), code)) {
                log.warn("2FA verification failed for user {}", userId);
                throw new TwoFactorException("Invalid OTP code");
            }
            log.info("2FA verification succeeded for user {}", userId);
            return new TwoFactorStatusResponse(true);
        }

        @Transactional
        TwoFactorStatusResponse disable(String userId, String code) {
            AuthFactor factor = required(userId);
            if (!factor.isEnabled() || !totp.valid(protector.decrypt(factor.encryptedSecret()), code)) {
                throw new TwoFactorException("Invalid OTP code");
            }
            factor.disable();
            return new TwoFactorStatusResponse(false);
        }

        TwoFactorStatusResponse status(String userId) {
            return new TwoFactorStatusResponse(factors.findByUserId(userId).map(AuthFactor::isEnabled).orElse(false));
        }

        private AuthFactor required(String userId) {
            return factors.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("2FA setup not found"));
        }

        private String qr(String uri) {
            try {
                BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 250, 250);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(matrix, "PNG", png);
                return Base64.getEncoder().encodeToString(png.toByteArray());
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to generate QR code", ex);
            }
        }

        private static String encode(String value) {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    @RestController
    @RequestMapping("/api/2fa")
    public static class TwoFactorController {
        private final TwoFactorService service;

        TwoFactorController(TwoFactorService service) {
            this.service = service;
        }

        @PostMapping("/setup")
        ApiResponse<TwoFactorSetupResponse> setup(org.springframework.security.core.Authentication authentication) {
            return ApiResponse.success("2FA setup created", service.setup(authentication.getName()));
        }

        @PostMapping("/verify-setup")
        ApiResponse<TwoFactorStatusResponse> verifySetup(org.springframework.security.core.Authentication authentication,
                                                          @Valid @RequestBody TwoFactorVerifyRequest request) {
            return ApiResponse.success("2FA enabled", service.verifySetup(authentication.getName(), request.otpCode()));
        }

        @PostMapping("/verify")
        ApiResponse<TwoFactorStatusResponse> verify(org.springframework.security.core.Authentication authentication,
                                                     @Valid @RequestBody TwoFactorVerifyRequest request) {
            return ApiResponse.success("OTP verified", service.verify(authentication.getName(), request.otpCode()));
        }

        @PostMapping("/disable")
        ApiResponse<TwoFactorStatusResponse> disable(org.springframework.security.core.Authentication authentication,
                                                      @Valid @RequestBody TwoFactorDisableRequest request) {
            return ApiResponse.success("2FA disabled", service.disable(authentication.getName(), request.otpCode()));
        }

        @GetMapping("/status")
        ApiResponse<TwoFactorStatusResponse> status(org.springframework.security.core.Authentication authentication) {
            return ApiResponse.success("2FA status", service.status(authentication.getName()));
        }
    }

    @RestController
    @RequestMapping("/internal/twofa")
    public static class InternalTwoFactorController {
        private final TwoFactorService service;
        private final String internalKey;

        InternalTwoFactorController(TwoFactorService service, @Value("${services.internal-api-key}") String internalKey) {
            this.service = service;
            this.internalKey = internalKey;
        }

        @GetMapping("/users/{userId}/status")
        TwoFactorStatusResponse status(@PathVariable String userId,
                                       @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey) {
            verifyKey(suppliedKey);
            return service.status(userId);
        }

        @PostMapping("/verify")
        ResponseEntity<Void> verify(@RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
                                    @Valid @RequestBody InternalOtpVerification request) {
            verifyKey(suppliedKey);
            service.verify(request.userId(), request.otpCode());
            return ResponseEntity.noContent().build();
        }

        private void verifyKey(String suppliedKey) {
            if (!internalKey.equals(suppliedKey)) throw new UnauthorizedException("Invalid internal API key");
        }
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        ResponseEntity<ErrorResponse> missing(ResourceNotFoundException ex, HttpServletRequest request) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }

        @ExceptionHandler({TwoFactorException.class, UnauthorizedException.class})
        ResponseEntity<ErrorResponse> badRequest(RuntimeException ex, HttpServletRequest request) {
            return error(ex instanceof UnauthorizedException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest request) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
        }

        private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
            return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
        }
    }

    public static class ResourceNotFoundException extends RuntimeException { ResourceNotFoundException(String message) { super(message); } }
    public static class TwoFactorException extends RuntimeException { TwoFactorException(String message) { super(message); } }
    public static class UnauthorizedException extends RuntimeException { UnauthorizedException(String message) { super(message); } }
}
