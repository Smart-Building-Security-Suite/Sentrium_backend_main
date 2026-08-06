package com.securitysuite.backend.emergency;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmergencyEventDto(
        UUID id,
        EmergencyEventType eventType,
        EmergencySeverity severity,
        EmergencyStatus status,
        UUID triggeredById,
        String triggeredByName,
        Instant triggeredAt,
        Instant resolvedAt,
        String affectedZones,
        String description,
        String responseActions,
        Instant allClearAt
) {
    public static EmergencyEventDto from(EmergencyEvent event) {
        return new EmergencyEventDto(
                event.getId(),
                event.getEventType(),
                event.getSeverity(),
                event.getStatus(),
                event.getTriggeredBy() != null ? event.getTriggeredBy().getId() : null,
                event.getTriggeredBy() != null ? event.getTriggeredBy().getName() : null,
                event.getTriggeredAt(),
                event.getResolvedAt(),
                event.getAffectedZones(),
                event.getDescription(),
                event.getResponseActions(),
                event.getAllClearAt()
        );
    }
}
