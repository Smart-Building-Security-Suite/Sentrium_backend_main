package com.securitysuite.backend.emergency;

import java.util.UUID;

public record EmergencyContactDto(
        UUID id,
        String name,
        String role,
        String phoneNumber,
        String email,
        Integer priority,
        Boolean enabled
) {
    public static EmergencyContactDto from(EmergencyContact contact) {
        return new EmergencyContactDto(
                contact.getId(),
                contact.getName(),
                contact.getRole(),
                contact.getPhoneNumber(),
                contact.getEmail(),
                contact.getPriority(),
                contact.getEnabled()
        );
    }
}
