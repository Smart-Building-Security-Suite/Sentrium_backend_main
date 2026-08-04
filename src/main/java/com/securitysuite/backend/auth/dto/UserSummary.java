package com.securitysuite.backend.auth.dto;

import com.securitysuite.backend.user.Role;
import com.securitysuite.backend.user.User;

import java.util.UUID;

public record UserSummary(UUID id, String name, String phoneNumber, Role role) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getPhoneNumber(), user.getRole());
    }
}
