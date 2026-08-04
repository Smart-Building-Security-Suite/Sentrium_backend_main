package com.securitysuite.backend.surveillance.dto;

import java.time.Instant;

public record MotionEventDto(
        Long id,
        String cameraId,
        String cameraName,
        Instant detectedAt,
        double confidence
) {}
