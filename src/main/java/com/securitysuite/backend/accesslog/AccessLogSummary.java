package com.securitysuite.backend.accesslog;

import java.time.Instant;
import java.util.UUID;

public record AccessLogSummary(
        UUID id,
        UUID userId,
        String userEmail,
        UUID deviceId,
        String deviceName,
        UUID zoneId,
        String zoneName,
        AccessResult result,
        Instant timestamp
) {
    public static AccessLogSummary from(AccessLog accessLog) {
        return new AccessLogSummary(
                accessLog.getId(),
                accessLog.getUser().getId(),
                accessLog.getUser().getEmail(),
                accessLog.getDevice().getId(),
                accessLog.getDevice().getName(),
                accessLog.getZone().getId(),
                accessLog.getZone().getName(),
                accessLog.getResult(),
                accessLog.getTimestamp()
        );
    }
}
