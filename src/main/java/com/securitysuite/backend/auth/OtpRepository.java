package com.securitysuite.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpRecord, Long> {

    /** Returns the most recent OTP record for the given phone number. */
    Optional<OtpRecord> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    @Modifying
    @Transactional
    void deleteByPhoneNumber(String phoneNumber);

    /** Housekeeping: remove OTP records whose expiry has passed. */
    @Modifying
    @Transactional
    void deleteByExpiresAtBefore(Instant now);
}
