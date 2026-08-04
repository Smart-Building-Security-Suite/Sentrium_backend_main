package com.securitysuite.backend.surveillance.dto;

import java.time.Instant;

public record FeedStatusDto(
        String cameraId,
        String status,           // "ONLINE" or "OFFLINE"
        Instant lastHeartbeatAt,
        String resolution        // e.g. "1080p"
) {}
