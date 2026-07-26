package com.oracle.banking.branch;

import com.oracle.banking.shared.constants.SecurityConstants;
import com.oracle.banking.shared.response.ApiResponse;
import com.oracle.banking.shared.response.ErrorResponse;
import com.oracle.banking.branch.entity.Branch;
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
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.CommandLineRunner;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication
@EntityScan(basePackageClasses = BranchServiceApplication.class)
@EnableJpaRepositories(basePackageClasses = BranchServiceApplication.class, considerNestedRepositories = true)
@OpenAPIDefinition(info = @Info(title = "Branch Service", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class BranchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BranchServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner initialBranches(BranchRepository branches) {
        return arguments -> {
            if (branches.count() == 0) {
                branches.saveAll(List.of(
                        new Branch(UUID.randomUUID().toString(), "Bengaluru Main", "ORCL0000001", "Bengaluru", "Karnataka"),
                        new Branch(UUID.randomUUID().toString(), "Mumbai Fort", "ORCL0000002", "Mumbai", "Maharashtra"),
                        new Branch(UUID.randomUUID().toString(), "Chennai Central", "ORCL0000003", "Chennai", "Tamil Nadu")));
            }
        };
    }

    public interface BranchRepository extends JpaRepository<Branch, String> {
        Optional<Branch> findByIfsc(String ifsc);
    }

    public record BranchSummaryResponse(String branchId, String branchName, String ifsc, String city) {
        static BranchSummaryResponse from(Branch branch) {
            return new BranchSummaryResponse(branch.getBranchId(), branch.getBranchName(), branch.getIfsc(), branch.getCity());
        }
    }

    public record BranchResponse(String branchId, String branchName, String ifsc, String city, String state) {
        static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getBranchId(), branch.getBranchName(), branch.getIfsc(), branch.getCity(), branch.getState());
        }
    }

    @Component
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {
        private final SecretKey key;

        JwtAuthenticationFilter(@org.springframework.beans.factory.annotation.Value("${security.jwt.secret}") String secret) {
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
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                            .anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Service
    public static class BranchService {
        private static final Logger log = LoggerFactory.getLogger(BranchService.class);
        private final BranchRepository branches;

        BranchService(BranchRepository branches) {
            this.branches = branches;
        }

        List<BranchSummaryResponse> all() {
            return branches.findAll().stream().map(BranchSummaryResponse::from).toList();
        }

        BranchResponse byId(String id) {
            log.info("Branch lookup by id");
            return BranchResponse.from(branches.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found")));
        }

        BranchResponse byIfsc(String ifsc) {
            log.info("Branch lookup by IFSC");
            return BranchResponse.from(branches.findByIfsc(ifsc)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found")));
        }
    }

    @RestController
    @Validated
    @RequestMapping("/api/branches")
    public static class BranchController {
        private final BranchService service;

        BranchController(BranchService service) {
            this.service = service;
        }

        @GetMapping
        ApiResponse<List<BranchSummaryResponse>> all() {
            return ApiResponse.success("Branches", service.all());
        }

        @GetMapping("/ifsc/{ifsc}")
        ApiResponse<BranchResponse> byIfsc(
                @PathVariable @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC") String ifsc) {
            return ApiResponse.success("Branch", service.byIfsc(ifsc));
        }

        @GetMapping("/{id}")
        ApiResponse<BranchResponse> byId(@PathVariable String id) {
            return ApiResponse.success("Branch", service.byId(id));
        }
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        ResponseEntity<ErrorResponse> missing(ResourceNotFoundException ex, HttpServletRequest request) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest request) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("An unexpected error occurred", request.getRequestURI()));
        }
    }

    public static class ResourceNotFoundException extends RuntimeException { ResourceNotFoundException(String message) { super(message); } }
}
