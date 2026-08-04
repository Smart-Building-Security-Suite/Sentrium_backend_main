package com.securitysuite.backend.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String name, String phoneNumber, Role role, boolean active, Instant createdAt) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
