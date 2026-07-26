package com.oracle.banking.auth;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.shared.validation.PasswordPolicy;
import com.oracle.banking.auth.entity.AppUser;
import com.oracle.banking.auth.entity.Role;
import com.oracle.banking.auth.entity.UserSession;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication
@EntityScan(basePackageClasses = AuthServiceApplication.class)
@EnableJpaRepositories(basePackageClasses = AuthServiceApplication.class, considerNestedRepositories = true)
@OpenAPIDefinition(info = @Info(title = "Authentication Service", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner defaultRoles(RoleRepository roles) {
        return arguments -> {
            ensureRole(roles, SecurityConstants.CUSTOMER_ROLE);
            ensureRole(roles, SecurityConstants.ADMIN_ROLE);
        };
    }

    private static void ensureRole(RoleRepository roles, String name) {
        if (!roles.existsByRoleName(name)) {
            roles.save(new Role(UUID.randomUUID().toString(), name));
        }
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public interface AppUserRepository extends JpaRepository<AppUser, String> {
        boolean existsByUsername(String username);
        boolean existsByEmail(String email);
        Optional<AppUser> findByUsernameOrEmail(String username, String email);
    }

    public interface RoleRepository extends JpaRepository<Role, String> {
        boolean existsByRoleName(String roleName);
        Optional<Role> findByRoleName(String roleName);
    }

    public interface UserSessionRepository extends JpaRepository<UserSession, String> {
        List<UserSession> findByUserIdAndStatus(String userId, String status);
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 60) String username,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[+]?[0-9]{7,15}$") String phone,
            @NotBlank String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record RegisterResponse(String userId, String username, String email, String role, boolean twoFactorEnabled) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password, String otpCode) {
    }

    public record LoginResponse(String token, String tokenType, Instant expiresAt, String username, String role,
                                boolean twoFactorEnabled) {
    }

    public record UserResponse(String userId, String username, String email, String role) {
    }

    public record CustomerProfileCreate(String userId, String fullName, String email, String phone) {
    }

    public record TwoFactorStatus(boolean enabled) {
    }

    public record OtpVerification(String userId, String otpCode) {
    }

    @Service
    public static class JwtService {
        private final String secret;
        private final long expirationMinutes;

        JwtService(@Value("${security.jwt.secret}") String secret,
                   @Value("${security.jwt.expiration-minutes:30}") long expirationMinutes) {
            this.secret = secret;
            this.expirationMinutes = expirationMinutes;
        }

        Token issue(AppUser user) {
            Instant expiresAt = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);
            String token = Jwts.builder()
                    .subject(user.getUserId())
                    .claim("username", user.getUsername())
                    .claim("roles", List.of(user.getRole().getRoleName()))
                    .issuedAt(new Date())
                    .expiration(Date.from(expiresAt))
                    .signWith(key())
                    .compact();
            return new Token(token, expiresAt);
        }

        Claims parse(String token) {
            return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        }

        private SecretKey key() {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        }
    }

    public record Token(String value, Instant expiresAt) {
    }

    @Component
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {
        private final JwtService jwtService;

        JwtAuthenticationFilter(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    Claims claims = jwtService.parse(header.substring(SecurityConstants.BEARER_PREFIX.length()));
                    @SuppressWarnings("unchecked")
                    Collection<String> roles = claims.get("roles", List.class);
                    var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                    var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request, response);
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
                            .requestMatchers("/api/auth/register", "/api/auth/login", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                            .anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Service
    public static class CustomerClient {
        private final RestClient client;
        private final String customerServiceUrl;
        private final String internalKey;

        CustomerClient(RestClient client,
                       @Value("${services.customer.base-url}") String customerServiceUrl,
                       @Value("${services.internal-api-key}") String internalKey) {
            this.client = client;
            this.customerServiceUrl = customerServiceUrl;
            this.internalKey = internalKey;
        }

        void createProfile(AppUser user, RegisterRequest request) {
            client.post().uri(customerServiceUrl + "/internal/customers")
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                    .body(new CustomerProfileCreate(user.getUserId(), request.fullName(), user.getEmail(), request.phone()))
                    .retrieve().toBodilessEntity();
        }
    }

    @Service
    public static class TwoFactorClient {
        private final RestClient client;
        private final String twoFactorServiceUrl;
        private final String internalKey;

        TwoFactorClient(RestClient client,
                        @Value("${services.twofa.base-url}") String twoFactorServiceUrl,
                        @Value("${services.internal-api-key}") String internalKey) {
            this.client = client;
            this.twoFactorServiceUrl = twoFactorServiceUrl;
            this.internalKey = internalKey;
        }

        boolean isEnabled(String userId) {
            TwoFactorStatus status = client.get().uri(twoFactorServiceUrl + "/internal/twofa/users/{id}/status", userId)
                    .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                    .retrieve().body(TwoFactorStatus.class);
            return status != null && status.enabled();
        }

        void verify(String userId, String otpCode) {
            try {
                client.post().uri(twoFactorServiceUrl + "/internal/twofa/verify")
                        .header(SecurityConstants.INTERNAL_API_KEY_HEADER, internalKey)
                        .body(new OtpVerification(userId, otpCode))
                        .retrieve().toBodilessEntity();
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    throw new TwoFactorException("Invalid OTP code");
                }
                throw ex;
            }
        }
    }

    @Service
    public static class AuthenticationService {
        private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
        private final AppUserRepository users;
        private final RoleRepository roles;
        private final UserSessionRepository sessions;
        private final PasswordEncoder passwordEncoder;
        private final CustomerClient customerClient;
        private final TwoFactorClient twoFactorClient;
        private final JwtService jwtService;

        AuthenticationService(AppUserRepository users, RoleRepository roles, UserSessionRepository sessions,
                              PasswordEncoder passwordEncoder, CustomerClient customerClient,
                              TwoFactorClient twoFactorClient, JwtService jwtService) {
            this.users = users;
            this.roles = roles;
            this.sessions = sessions;
            this.passwordEncoder = passwordEncoder;
            this.customerClient = customerClient;
            this.twoFactorClient = twoFactorClient;
            this.jwtService = jwtService;
        }

        @Transactional
        RegisterResponse register(RegisterRequest request) {
            if (!PasswordPolicy.isValid(request.password())) {
                throw new BadRequestException("Password must contain upper-case, lower-case, number, special character and be at least 8 characters");
            }
            if (users.existsByUsername(request.username())) throw new DuplicateResourceException("Username already exists");
            if (users.existsByEmail(request.email())) throw new DuplicateResourceException("Email already exists");
            Role customer = roles.findByRoleName(SecurityConstants.CUSTOMER_ROLE)
                    .orElseThrow(() -> new IllegalStateException("Customer role is not configured"));
            AppUser user = users.save(new AppUser(UUID.randomUUID().toString(), customer, request.username(),
                    request.email(), request.phone(), passwordEncoder.encode(request.password())));
            customerClient.createProfile(user, request);
            log.info("Registration completed for user {}", user.getUserId());
            return new RegisterResponse(user.getUserId(), user.getUsername(), user.getEmail(), customer.getRoleName(), false);
        }

        @Transactional
        LoginResponse login(LoginRequest request) {
            AppUser user = users.findByUsernameOrEmail(request.username(), request.username())
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
            if (!"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            boolean twoFactorEnabled = twoFactorClient.isEnabled(user.getUserId());
            if (twoFactorEnabled) {
                if (request.otpCode() == null || request.otpCode().isBlank()) throw new TwoFactorException("OTP code is required");
                twoFactorClient.verify(user.getUserId(), request.otpCode());
            }
            Token token = jwtService.issue(user);
            sessions.save(new UserSession(user.getUserId(), token.expiresAt()));
            log.info("Login successful for user {}", user.getUserId());
            return new LoginResponse(token.value(), "Bearer", token.expiresAt(), user.getUsername(),
                    user.getRole().getRoleName(), twoFactorEnabled);
        }

        @Transactional
        void logout(String userId) {
            sessions.findByUserIdAndStatus(userId, "ACTIVE").forEach(UserSession::invalidate);
            log.info("Logout completed for user {}", userId);
        }

        UserResponse currentUser(String userId) {
            AppUser user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return new UserResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getRole().getRoleName());
        }
    }

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthenticationController {
        private final AuthenticationService service;

        AuthenticationController(AuthenticationService service) {
            this.service = service;
        }

        @PostMapping("/register")
        ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered", service.register(request)));
        }

        @PostMapping("/login")
        ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.success("Login successful", service.login(request)));
        }

        @PostMapping("/logout")
        ApiResponse<Void> logout(org.springframework.security.core.Authentication authentication) {
            service.logout(authentication.getName());
            return ApiResponse.success("Logout successful", null);
        }

        @GetMapping("/me")
        ApiResponse<UserResponse> me(org.springframework.security.core.Authentication authentication) {
            return ApiResponse.success("Authenticated user", service.currentUser(authentication.getName()));
        }
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {
        @ExceptionHandler(DuplicateResourceException.class)
        ResponseEntity<ErrorResponse> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
            return error(HttpStatus.CONFLICT, ex.getMessage(), request);
        }

        @ExceptionHandler({BadRequestException.class, TwoFactorException.class})
        ResponseEntity<ErrorResponse> badRequest(RuntimeException ex, HttpServletRequest request) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        }

        @ExceptionHandler(BadCredentialsException.class)
        ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException ex, HttpServletRequest request) {
            return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        ResponseEntity<ErrorResponse> missing(ResourceNotFoundException ex, HttpServletRequest request) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest request) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
        }

        private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
            return ResponseEntity.status(status).body(ErrorResponse.of(message, request.getRequestURI()));
        }
    }

    public static class DuplicateResourceException extends RuntimeException { DuplicateResourceException(String message) { super(message); } }
    public static class ResourceNotFoundException extends RuntimeException { ResourceNotFoundException(String message) { super(message); } }
    public static class BadRequestException extends RuntimeException { BadRequestException(String message) { super(message); } }
    public static class BadCredentialsException extends RuntimeException { BadCredentialsException(String message) { super(message); } }
    public static class TwoFactorException extends RuntimeException { TwoFactorException(String message) { super(message); } }
}
