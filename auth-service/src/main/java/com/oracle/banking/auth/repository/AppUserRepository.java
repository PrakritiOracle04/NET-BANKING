package com.oracle.banking.auth.repository;

import com.oracle.banking.auth.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<AppUser> findByUsernameOrEmail(String username, String email);
    Optional<AppUser> findByUsername(String username);
    @Query("select user from AppUser user where lower(user.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Page<AppUser> findByStatus(String status, Pageable pageable);
    long countByStatus(String status);
}
