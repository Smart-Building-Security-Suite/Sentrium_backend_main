package com.securitysuite.backend.mobileaccess;

import java.time.Instant;
import java.util.UUID;

public record MobileAccessTokenDto(
        UUID id,
        UUID userId,
        String userName,
        String qrCodeData,
        UUID deviceId,
        String deviceName,
        UUID zoneId,
        String zoneName,
        Instant createdAt,
        Instant expiresAt,
        Integer usesRemaining,
        Integer usedCount,
        Instant lastUsedAt,
        Boolean revoked,
        String purpose,
        Boolean isExpired,
        Boolean isValid
) {
    public static MobileAccessTokenDto from(MobileAccessToken token) {
        boolean expired = token.getExpiresAt().isBefore(Instant.now());
        boolean usesExhausted = token.getUsesRemaining() != null && token.getUsedCount() >= token.getUsesRemaining();
        boolean valid = !token.getRevoked() && !expired && !usesExhausted;

        return new MobileAccessTokenDto(
                token.getId(),
                token.getUser().getId(),
                token.getUser().getName(),
                token.getQrCodeData(),
                token.getDevice() != null ? token.getDevice().getId() : null,
                token.getDevice() != null ? token.getDevice().getName() : null,
                token.getZone() != null ? token.getZone().getId() : null,
                token.getZone() != null ? token.getZone().getName() : null,
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getUsesRemaining(),
                token.getUsedCount(),
                token.getLastUsedAt(),
                token.getRevoked(),
                token.getPurpose(),
                expired,
                valid
        );
    }
}
