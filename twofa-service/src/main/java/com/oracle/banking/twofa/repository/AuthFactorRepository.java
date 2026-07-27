package com.oracle.banking.twofa.repository;
import com.oracle.banking.twofa.entity.AuthFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AuthFactorRepository extends JpaRepository<AuthFactor, String> { Optional<AuthFactor> findByUserId(String userId); }
