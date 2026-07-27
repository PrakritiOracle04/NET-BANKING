package com.oracle.banking.auth;

import com.oracle.banking.auth.entity.Role;
import com.oracle.banking.auth.repository.RoleRepository;
import com.oracle.banking.shared.constants.SecurityConstants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@SpringBootApplication
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

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void ensureRole(RoleRepository roles, String roleName) {
        if (!roles.existsByRoleName(roleName)) {
            roles.save(new Role(UUID.randomUUID().toString(), roleName));
        }
    }
}
