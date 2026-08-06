package com.securitysuite.backend.mobileaccess;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MobileAccessTokenRepository extends JpaRepository<MobileAccessToken, UUID> {
    Optional<MobileAccessToken> findByQrCodeData(String qrCodeData);

    List<MobileAccessToken> findByUserId(UUID userId);

    @Query("SELECT t FROM MobileAccessToken t WHERE t.user.id = :userId AND t.revoked = false AND t.expiresAt > :now")
    List<MobileAccessToken> findActiveTokensByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("DELETE FROM MobileAccessToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") Instant now);
}
