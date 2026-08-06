package com.securitysuite.backend.device;

import java.util.UUID;

/**
 * Safe API response for a Device — decouples the API contract from the JPA entity
 * and prevents N+1 lazy-loading of the nested Zone association on serialization.
 */
public record DeviceDto(
        UUID id,
        String name,
        DeviceType type,
        DeviceStatus status,
        UUID zoneId,
        String zoneName,
        Boolean active
) {
    public static DeviceDto from(Device device) {
        return new DeviceDto(
                device.getId(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getZone().getId(),
                device.getZone().getName(),
                device.getActive()
        );
    }
}
