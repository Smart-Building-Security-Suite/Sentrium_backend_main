package com.securitysuite.backend.patrol;

import java.time.Instant;
import java.util.UUID;

public record PatrolSessionDto(
        UUID id,
        UUID routeId,
        String routeName,
        UUID officerId,
        String officerName,
        Instant startedAt,
        Instant completedAt,
        PatrolSessionStatus status,
        String notes,
        Integer checkpointsScanned,
        Integer totalCheckpoints,
        Double completionPercentage
) {
    public static PatrolSessionDto from(PatrolSession session) {
        int totalCheckpoints = session.getRoute().getCheckpoints().size();
        int scanned = session.getScans() != null ? session.getScans().size() : 0;
        double completion = totalCheckpoints > 0 ? (scanned * 100.0 / totalCheckpoints) : 0.0;

        return new PatrolSessionDto(
                session.getId(),
                session.getRoute().getId(),
                session.getRoute().getName(),
                session.getOfficer().getId(),
                session.getOfficer().getName(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getStatus(),
                session.getNotes(),
                scanned,
                totalCheckpoints,
                Math.round(completion * 100.0) / 100.0
        );
    }
}
