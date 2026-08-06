package com.securitysuite.backend.anomaly;

import java.time.Instant;
import java.util.UUID;

public record AnomalyDto(
        UUID id,
        AnomalyType anomalyType,
        AnomalySeverity severity,
        String entityType,
        UUID entityId,
        String description,
        String detailsJson,
        Instant detectedAt,
        Boolean reviewed,
        UUID reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        Boolean falsePositive,
        String actionTaken,
        Double confidenceScore
) {
    public static AnomalyDto from(Anomaly anomaly) {
        return new AnomalyDto(
                anomaly.getId(),
                anomaly.getAnomalyType(),
                anomaly.getSeverity(),
                anomaly.getEntityType(),
                anomaly.getEntityId(),
                anomaly.getDescription(),
                anomaly.getDetailsJson(),
                anomaly.getDetectedAt(),
                anomaly.getReviewed(),
                anomaly.getReviewedBy() != null ? anomaly.getReviewedBy().getId() : null,
                anomaly.getReviewedBy() != null ? anomaly.getReviewedBy().getName() : null,
                anomaly.getReviewedAt(),
                anomaly.getFalsePositive(),
                anomaly.getActionTaken(),
                anomaly.getConfidenceScore()
        );
    }
}
