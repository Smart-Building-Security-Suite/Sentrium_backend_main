package com.securitysuite.backend.surveillance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record CreateMotionEventRequest(
        @NotBlank String cameraId,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence
) {}
