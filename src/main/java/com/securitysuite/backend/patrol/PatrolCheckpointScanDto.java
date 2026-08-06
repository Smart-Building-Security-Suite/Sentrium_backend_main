package com.securitysuite.backend.patrol;

import java.time.Instant;
import java.util.UUID;

public record PatrolCheckpointScanDto(
        UUID id,
        UUID sessionId,
        UUID checkpointId,
        String checkpointName,
        Instant scannedAt,
        Boolean incidentReported,
        UUID incidentId,
        String notes
) {
    public static PatrolCheckpointScanDto from(PatrolCheckpointScan scan) {
        return new PatrolCheckpointScanDto(
                scan.getId(),
                scan.getSession().getId(),
                scan.getCheckpoint().getId(),
                scan.getCheckpoint().getName(),
                scan.getScannedAt(),
                scan.getIncidentReported(),
                scan.getIncident() != null ? scan.getIncident().getId() : null,
                scan.getNotes()
        );
    }
}
