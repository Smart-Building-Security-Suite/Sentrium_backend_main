package com.securitysuite.backend.user;

import java.util.UUID;

public record UserDto(UUID id, String fullName, String email, Role role) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
