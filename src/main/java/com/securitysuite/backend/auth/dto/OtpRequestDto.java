package com.securitysuite.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(@NotBlank String phoneNumber) {
}
