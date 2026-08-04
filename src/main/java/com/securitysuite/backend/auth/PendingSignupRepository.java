package com.securitysuite.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PendingSignupRepository extends JpaRepository<PendingSignup, Long> {

    Optional<PendingSignup> findBySignupToken(String token);

    @Modifying
    @Transactional
    void deleteByPhoneNumber(String phoneNumber);
}
