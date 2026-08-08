package com.securitysuite.backend.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentDto(
        UUID id,
        String title,
        String description,
        IncidentType type,
        IncidentSeverity severity,
        IncidentStatus status,
        UUID zoneId,
        String zoneName,
        String location,
        UUID reportedById,
        String reportedByName,
        Instant reportedAt,
        UUID assignedToId,
        String assignedToName,
        Instant occurredAt,
        Instant resolvedAt,
        String resolution,
        List<String> evidenceUrls,
        List<String> involvedParties,
        String actionsTaken,
        Boolean requiresFollowUp,
        Instant followUpDate,
        String tags
) {
    public static IncidentDto from(Incident incident) {
        return new IncidentDto(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getType(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getZone() != null ? incident.getZone().getId() : null,
                incident.getZone() != null ? incident.getZone().getName() : null,
                incident.getLocation(),
                incident.getReportedBy() != null ? incident.getReportedBy().getId() : null,
                incident.getReportedBy() != null ? incident.getReportedBy().getName() : null,
                incident.getReportedAt(),
                incident.getAssignedTo() != null ? incident.getAssignedTo().getId() : null,
                incident.getAssignedTo() != null ? incident.getAssignedTo().getName() : null,
                incident.getOccurredAt(),
                incident.getResolvedAt(),
                incident.getResolution(),
                incident.getEvidenceUrls(),
                incident.getInvolvedParties(),
                incident.getActionsTaken(),
                incident.getRequiresFollowUp(),
                incident.getFollowUpDate(),
                incident.getTags()
        );
    }
}
