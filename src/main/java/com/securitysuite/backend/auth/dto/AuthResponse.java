package com.securitysuite.backend.auth.dto;

public record AuthResponse(String accessToken, long expiresIn, UserSummary user) {
}
