package com.securitysuite.backend.patrol;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatrolRouteDto(
        UUID id,
        String name,
        String description,
        Integer estimatedDurationMinutes,
        Boolean enabled,
        Instant createdAt,
        Integer checkpointCount
) {
    public static PatrolRouteDto from(PatrolRoute route) {
        return new PatrolRouteDto(
                route.getId(),
                route.getName(),
                route.getDescription(),
                route.getEstimatedDurationMinutes(),
                route.getEnabled(),
                route.getCreatedAt(),
                route.getCheckpoints() != null ? route.getCheckpoints().size() : 0
        );
    }
}
