package com.securitysuite.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Final step of the OTP signup flow.
 * The client presents the signupToken received from POST /auth/signup/otp/verify
 * together with the desired display name and password.
 */
public record RegisterRequest(
        @NotBlank String signupToken,
        @NotBlank String name,
        @NotBlank @Size(min = 8) String password
) {
}
