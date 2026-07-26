package com.securitysuite.backend.auth.dto;

import com.securitysuite.backend.user.Role;
import com.securitysuite.backend.user.User;

import java.util.UUID;

public record UserSummary(UUID id, String fullName, String email, Role role) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
