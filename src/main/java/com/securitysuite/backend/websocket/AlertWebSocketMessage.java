package com.securitysuite.backend.websocket;

import com.securitysuite.backend.alert.AlertSeverity;
import com.securitysuite.backend.alert.AlertStatus;

import java.time.Instant;
import java.util.UUID;

public record AlertWebSocketMessage(
        String type, // "ALERT_CREATED", "ALERT_ACKNOWLEDGED", "ALERT_RESOLVED"
        UUID alertId,
        String message,
        AlertSeverity severity,
        AlertStatus status,
        UUID zoneId,
        String zoneName,
        UUID deviceId,
        String deviceName,
        Instant timestamp
) {}
