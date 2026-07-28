package com.securitysuite.backend.zone;

import java.util.UUID;

/**
 * Safe API response for a Zone — decouples the API contract from the JPA entity.
 */
public record ZoneDto(
        UUID id,
        String name,
        String floor,
        String building
) {
    public static ZoneDto from(Zone zone) {
        return new ZoneDto(zone.getId(), zone.getName(), zone.getFloor(), zone.getBuilding());
    }
}
