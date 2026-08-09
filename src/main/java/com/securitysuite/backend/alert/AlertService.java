package com.securitysuite.backend.alert;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.notification.AlertCreatedEvent;
import com.securitysuite.backend.pushnotification.PushNotificationService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.websocket.WebSocketAlertPublisher;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WebSocketAlertPublisher webSocketPublisher;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    /**
     * List alerts with database-level filtering and pagination using JPA Specification
     */
    public org.springframework.data.domain.Page<Alert> list(
            AlertStatus status,
            AlertSeverity severity,
            UUID zoneId,
            UUID deviceId,
            org.springframework.data.domain.Pageable pageable) {

        org.springframework.data.jpa.domain.Specification<Alert> spec =
            org.springframework.data.jpa.domain.Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (severity != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
        }
        if (zoneId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("zone").get("id"), zoneId));
        }
        if (deviceId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("device").get("id"), deviceId));
        }

        return alertRepository.findAll(spec, pageable);
    }

    @Transactional
    public Alert create(CreateAlertRequest request) {
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new NotFoundException("Zone not found"));
        Device device = request.deviceId() == null ? null : deviceRepository.findById(request.deviceId()).orElseThrow(() -> new NotFoundException("Device not found"));
        Alert alert = new Alert();
        alert.setZone(zone);
        alert.setDevice(device);
        alert.setSeverity(request.severity());
        alert.setMessage(request.message());
        alert.setStatus(AlertStatus.OPEN);
        alert = alertRepository.save(alert);
        eventPublisher.publishEvent(new AlertCreatedEvent(alert));

        if (pushNotificationService != null) {
            String emoji = switch (alert.getSeverity()) {
                case CRITICAL -> "🚨";
                case HIGH -> "⚠️";
                case MEDIUM -> "⚡";
                case LOW -> "ℹ️";
            };

            UUID excludeUserId = null;
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                excludeUserId = userRepository.findByPhoneNumber(auth.getName())
                        .map(User::getId)
                        .orElse(null);
            }

            pushNotificationService.sendToSecurityPersonnelExcluding(
                emoji + " New Alert: " + alert.getSeverity(),
                alert.getMessage() + " in " + zone.getName(),
                Map.of(
                    "alertId", alert.getId().toString(),
                    "severity", alert.getSeverity().name(),
                    "zoneId", zone.getId().toString(),
                    "zoneName", zone.getName(),
                    "type", "ALERT_CREATED"
                ),
                excludeUserId
            );
        }

        return alert;
    }

    public Alert get(UUID id) {
        return alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Alert not found"));
    }

    @Transactional
    public Alert acknowledge(UUID id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Alert not found"));
        if (alert.getStatus() != AlertStatus.ACKNOWLEDGED) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(Instant.now());
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "usr_xx";
            alert.setAcknowledgedBy(username);
            // Broadcast update via WebSocket
            webSocketPublisher.broadcastAlertUpdate(alert, "ALERT_ACKNOWLEDGED");
        }
        return alert;
    }

    @Transactional
    public Alert resolve(UUID id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Alert not found"));
        if (alert.getStatus() != AlertStatus.RESOLVED) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            // Broadcast update via WebSocket
            webSocketPublisher.broadcastAlertUpdate(alert, "ALERT_RESOLVED");
        }
        return alert;
    }

    public List<Alert> getRecentOpenAlerts(int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        return alertRepository.findRecentOpenAlerts(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED, pageable);
    }

    public record CreateAlertRequest(UUID zoneId, UUID deviceId, AlertSeverity severity, String message) {}
}
