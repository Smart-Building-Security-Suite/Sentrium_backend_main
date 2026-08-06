package com.securitysuite.backend.anomaly;

import com.securitysuite.backend.accesslog.AccessLog;
import com.securitysuite.backend.accesslog.AccessLogRepository;
import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.pushnotification.PushNotificationService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyService {
    private final AnomalyRepository anomalyRepository;
    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public Page<AnomalyDto> listAll(AnomalyType type, AnomalySeverity severity, Boolean reviewed, Pageable pageable) {
        if (type != null) return anomalyRepository.findByAnomalyType(type, pageable).map(AnomalyDto::from);
        if (severity != null) return anomalyRepository.findBySeverity(severity, pageable).map(AnomalyDto::from);
        if (reviewed != null) return anomalyRepository.findByReviewed(reviewed, pageable).map(AnomalyDto::from);

        return anomalyRepository.findAll(pageable).map(AnomalyDto::from);
    }

    public List<AnomalyDto> getUnreviewed() {
        return anomalyRepository.findUnreviewedOrderedBySeverity().stream()
                .map(AnomalyDto::from)
                .toList();
    }

    public long countUnreviewed() {
        return anomalyRepository.countUnreviewed();
    }

    public AnomalyDto getById(UUID id) {
        return AnomalyDto.from(anomalyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Anomaly not found")));
    }

    @Transactional
    public AnomalyDto markReviewed(UUID id, String actionTaken, String reviewerPhoneNumber) {
        Anomaly anomaly = anomalyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Anomaly not found"));

        User reviewer = userRepository.findByPhoneNumber(reviewerPhoneNumber)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));

        anomaly.setReviewed(true);
        anomaly.setReviewedBy(reviewer);
        anomaly.setReviewedAt(Instant.now());
        anomaly.setActionTaken(actionTaken);

        anomaly = anomalyRepository.save(anomaly);
        log.info("Anomaly reviewed: id={}, reviewer={}", id, reviewer.getName());

        return AnomalyDto.from(anomaly);
    }

    @Transactional
    public AnomalyDto markFalsePositive(UUID id, String reviewerPhoneNumber) {
        Anomaly anomaly = anomalyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Anomaly not found"));

        User reviewer = userRepository.findByPhoneNumber(reviewerPhoneNumber)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));

        anomaly.setReviewed(true);
        anomaly.setFalsePositive(true);
        anomaly.setReviewedBy(reviewer);
        anomaly.setReviewedAt(Instant.now());

        anomaly = anomalyRepository.save(anomaly);
        log.info("Anomaly marked as false positive: id={}", id);

        return AnomalyDto.from(anomaly);
    }

    @Transactional
    public AnomalyDto createAnomaly(AnomalyType type, AnomalySeverity severity, String entityType,
                                   UUID entityId, String description, String detailsJson, Double confidenceScore) {
        Anomaly anomaly = new Anomaly();
        anomaly.setAnomalyType(type);
        anomaly.setSeverity(severity);
        anomaly.setEntityType(entityType);
        anomaly.setEntityId(entityId);
        anomaly.setDescription(description);
        anomaly.setDetailsJson(detailsJson);
        anomaly.setConfidenceScore(confidenceScore);

        anomaly = anomalyRepository.save(anomaly);
        log.warn("Anomaly detected: type={}, severity={}, entity={}:{}", type, severity, entityType, entityId);

        // 🔔 PUSH NOTIFICATION: High/Critical Anomaly Detected
        if (pushNotificationService != null &&
            (severity == AnomalySeverity.HIGH || severity == AnomalySeverity.CRITICAL)) {
            String emoji = severity == AnomalySeverity.CRITICAL ? "🚨" : "⚠️";
            pushNotificationService.sendToSecurityPersonnel(
                emoji + " Anomaly Detected: " + type.name(),
                description,
                Map.of(
                    "anomalyId", anomaly.getId().toString(),
                    "type", type.name(),
                    "severity", severity.name(),
                    "entityType", entityType != null ? entityType : "SYSTEM",
                    "confidence", confidenceScore != null ? confidenceScore : 0.0
                )
            );
        }

        return AnomalyDto.from(anomaly);
    }

    /**
     * Automated anomaly detection job - runs every 15 minutes
     * Analyzes access logs and detects suspicious patterns
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void detectAnomalies() {
        log.info("Running automated anomaly detection...");

        Instant now = Instant.now();
        Instant lookbackStart = now.minus(30, ChronoUnit.MINUTES);

        // Example 1: Detect rapid access attempts (>5 doors in 5 minutes)
        detectRapidAccessAttempts(lookbackStart, now);

        // Example 2: Detect after-hours access (between 10 PM and 6 AM)
        detectAfterHoursAccess(lookbackStart, now);

        // Example 3: Detect failed access spikes (>3 failures in 10 minutes)
        detectFailedAccessSpikes(lookbackStart, now);

        log.info("Anomaly detection completed");
    }

    private void detectRapidAccessAttempts(Instant start, Instant end) {
        // Simplified implementation - in production, use more sophisticated ML algorithms
        List<AccessLog> recentLogs = accessLogRepository.findByTimestampBetween(start, end);

        // Group by user and count accesses
        var accessCountsByUser = recentLogs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> log.getUser().getId(),
                        java.util.stream.Collectors.counting()
                ));

        accessCountsByUser.forEach((userId, count) -> {
            if (count > 5) {
                // Check if anomaly already exists
                List<Anomaly> existing = anomalyRepository.findByEntity("USER", userId);
                boolean alreadyDetected = existing.stream()
                        .anyMatch(a -> a.getAnomalyType() == AnomalyType.RAPID_ACCESS_ATTEMPTS
                                && a.getDetectedAt().isAfter(start));

                if (!alreadyDetected) {
                    createAnomaly(
                            AnomalyType.RAPID_ACCESS_ATTEMPTS,
                            count > 10 ? AnomalySeverity.HIGH : AnomalySeverity.MEDIUM,
                            "USER",
                            userId,
                            "User accessed " + count + " doors in 30 minutes",
                            "{\"accessCount\": " + count + ", \"timeWindow\": \"30 minutes\"}",
                            0.85
                    );
                }
            }
        });
    }

    private void detectAfterHoursAccess(Instant start, Instant end) {
        // Check if current time is between 10 PM and 6 AM
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 22 || hour < 6) {
            List<AccessLog> afterHoursLogs = accessLogRepository.findByTimestampBetween(start, end);

            for (AccessLog log : afterHoursLogs) {
                List<Anomaly> existing = anomalyRepository.findByEntity("USER", log.getUser().getId());
                boolean alreadyDetected = existing.stream()
                        .anyMatch(a -> a.getAnomalyType() == AnomalyType.AFTER_HOURS_ACCESS
                                && a.getDetectedAt().isAfter(start));

                if (!alreadyDetected) {
                    createAnomaly(
                            AnomalyType.AFTER_HOURS_ACCESS,
                            AnomalySeverity.MEDIUM,
                            "USER",
                            log.getUser().getId(),
                            "After-hours access detected for " + log.getUser().getName(),
                            "{\"device\": \"" + log.getDevice().getName() + "\", \"time\": \"" + log.getTimestamp() + "\"}",
                            0.90
                    );
                }
            }
        }
    }

    private void detectFailedAccessSpikes(Instant start, Instant end) {
        // Implementation would analyze failed access attempts
        // Placeholder for now
        log.debug("Checking for failed access spikes...");
    }
}
