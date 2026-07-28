package com.securitysuite.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    boolean existsByJti(String jti);

    /** Housekeeping: prune tokens that have naturally expired. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :now")
    void deleteExpiredBefore(@Param("now") Instant now);
}
