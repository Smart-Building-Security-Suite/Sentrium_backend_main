package com.securitysuite.backend.patrol;

import java.util.UUID;

public record PatrolCheckpointDto(
        UUID id,
        UUID routeId,
        String name,
        String location,
        UUID zoneId,
        String zoneName,
        Integer sequenceOrder,
        String qrCode,
        Boolean required
) {
    public static PatrolCheckpointDto from(PatrolCheckpoint checkpoint) {
        return new PatrolCheckpointDto(
                checkpoint.getId(),
                checkpoint.getRoute().getId(),
                checkpoint.getName(),
                checkpoint.getLocation(),
                checkpoint.getZone() != null ? checkpoint.getZone().getId() : null,
                checkpoint.getZone() != null ? checkpoint.getZone().getName() : null,
                checkpoint.getSequenceOrder(),
                checkpoint.getQrCode(),
                checkpoint.getRequired()
        );
    }
}
