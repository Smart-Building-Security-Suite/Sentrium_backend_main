package com.securitysuite.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request. The role field is intentionally removed from the public API —
 * all self-registered users are created as SECURITY_OFFICER by default.
 * Admin accounts must be provisioned directly in the database.
 */
public record RegisterRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password
) {
}
