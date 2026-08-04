package com.securitysuite.backend.alert;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe API response for an Alert — decouples the API contract from the JPA entity
 * and eliminates N+1 lazy-loading on serialization.
 */
public record AlertDto(
        UUID id,
        UUID zoneId,
        String zoneName,
        UUID deviceId,
        String deviceName,
        AlertSeverity severity,
        AlertStatus status,
        String message,
        Instant createdAt,
        Instant resolvedAt,
        Instant acknowledgedAt,
        String acknowledgedBy
) {
    public static AlertDto from(Alert alert) {
        return new AlertDto(
                alert.getId(),
                alert.getZone().getId(),
                alert.getZone().getName(),
                alert.getDevice() == null ? null : alert.getDevice().getId(),
                alert.getDevice() == null ? null : alert.getDevice().getName(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getMessage(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getAcknowledgedAt(),
                alert.getAcknowledgedBy()
        );
    }
}
