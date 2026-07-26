package com.oracle.banking.customer;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.customer.entity.CustomerProfile;
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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication
@EntityScan(basePackageClasses = CustomerServiceApplication.class)
@EnableJpaRepositories(basePackageClasses = CustomerServiceApplication.class, considerNestedRepositories = true)
@OpenAPIDefinition(info = @Info(title = "Customer Service", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }

    public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {
        Optional<CustomerProfile> findByUserId(String userId);
        boolean existsByUserId(String userId);
    }

    public record CustomerProfileCreateRequest(@NotBlank String userId, @NotBlank String fullName,
                                               @NotBlank @Email String email,
                                               @NotBlank @Pattern(regexp = "^[+]?[0-9]{7,15}$") String phone) {
    }

    public record CustomerProfileRequest(@NotBlank @Size(max = 120) String fullName,
                                         @NotBlank @Pattern(regexp = "^[+]?[0-9]{7,15}$") String phone,
                                         @Size(max = 160) String addressLine1, @Size(max = 160) String addressLine2,
                                         @Size(max = 80) String city, @Size(max = 80) String state,
                                         @Size(max = 80) String country, @Size(max = 20) String postalCode) {
    }

    public record CustomerProfileResponse(String customerId, String userId, String fullName, String email, String phone,
                                          String addressLine1, String addressLine2, String city, String state,
                                          String country, String postalCode, String kycStatus, String profileStatus) {
        static CustomerProfileResponse from(CustomerProfile profile) {
            return new CustomerProfileResponse(profile.getCustomerId(), profile.getUserId(), profile.getFullName(), profile.getEmail(),
                    profile.getPhone(), profile.getAddressLine1(), profile.getAddressLine2(), profile.getCity(), profile.getState(),
                    profile.getCountry(), profile.getPostalCode(), profile.getKycStatus(), profile.getProfileStatus());
        }
    }

    @Component
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {
        private final SecretKey key;

        JwtAuthenticationFilter(@Value("${security.jwt.secret}") String secret) {
            key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
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
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null,
                            roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
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
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, error) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Service
    public static class CustomerService {
        private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
        private final CustomerProfileRepository profiles;

        CustomerService(CustomerProfileRepository profiles) {
            this.profiles = profiles;
        }

        @Transactional
        CustomerProfileResponse create(CustomerProfileCreateRequest request) {
            if (profiles.existsByUserId(request.userId())) throw new DuplicateResourceException("Customer profile already exists");
            return CustomerProfileResponse.from(profiles.save(
                    new CustomerProfile(request.userId(), request.fullName(), request.email(), request.phone())));
        }

        CustomerProfileResponse ownProfile(String userId) {
            return CustomerProfileResponse.from(requiredByUserId(userId));
        }

        @Transactional
        CustomerProfileResponse updateOwnProfile(String userId, CustomerProfileRequest request) {
            CustomerProfile profile = requiredByUserId(userId);
            profile.update(request);
            log.info("Customer profile updated for user {}", userId);
            return CustomerProfileResponse.from(profile);
        }

        CustomerProfileResponse findById(String customerId) {
            return CustomerProfileResponse.from(profiles.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found")));
        }

        private CustomerProfile requiredByUserId(String userId) {
            return profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        }
    }

    @RestController
    @RequestMapping("/api/customers")
    public static class CustomerController {
        private final CustomerService service;

        CustomerController(CustomerService service) {
            this.service = service;
        }

        @GetMapping("/me")
        ApiResponse<CustomerProfileResponse> me(org.springframework.security.core.Authentication authentication) {
            return ApiResponse.success("Customer profile", service.ownProfile(authentication.getName()));
        }

        @PutMapping("/me")
        ApiResponse<CustomerProfileResponse> updateMe(org.springframework.security.core.Authentication authentication,
                                                       @Valid @RequestBody CustomerProfileRequest request) {
            return ApiResponse.success("Customer profile updated", service.updateOwnProfile(authentication.getName(), request));
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        ApiResponse<CustomerProfileResponse> byId(@PathVariable String id) {
            return ApiResponse.success("Customer profile", service.findById(id));
        }
    }

    @RestController
    @RequestMapping("/internal/customers")
    public static class InternalCustomerController {
        private final CustomerService service;
        private final String internalKey;

        InternalCustomerController(CustomerService service, @Value("${services.internal-api-key}") String internalKey) {
            this.service = service;
            this.internalKey = internalKey;
        }

        @PostMapping
        ResponseEntity<CustomerProfileResponse> create(
                @RequestHeader(SecurityConstants.INTERNAL_API_KEY_HEADER) String suppliedKey,
                @Valid @RequestBody CustomerProfileCreateRequest request) {
            if (!internalKey.equals(suppliedKey)) throw new UnauthorizedException("Invalid internal API key");
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
        }
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        ResponseEntity<ErrorResponse> missing(ResourceNotFoundException ex, HttpServletRequest request) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }

        @ExceptionHandler(DuplicateResourceException.class)
        ResponseEntity<ErrorResponse> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
            return error(HttpStatus.CONFLICT, ex.getMessage(), request);
        }

        @ExceptionHandler(UnauthorizedException.class)
        ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
            return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
        }

        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<ErrorResponse> forbidden(AccessDeniedException ex, HttpServletRequest request) {
            return error(HttpStatus.FORBIDDEN, "Access denied", request);
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
    public static class DuplicateResourceException extends RuntimeException { DuplicateResourceException(String message) { super(message); } }
    public static class UnauthorizedException extends RuntimeException { UnauthorizedException(String message) { super(message); } }
}
