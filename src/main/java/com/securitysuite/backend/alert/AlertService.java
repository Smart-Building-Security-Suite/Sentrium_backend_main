package com.securitysuite.backend.alert;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.notification.AlertCreatedEvent;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<Alert> list(AlertStatus status, AlertSeverity severity) {
        if (status != null && severity != null) return alertRepository.findByStatusAndSeverity(status, severity);
        if (status != null) return alertRepository.findByStatus(status);
        if (severity != null) return alertRepository.findBySeverity(severity);
        return alertRepository.findAll();
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
        return alert;
    }

    @Transactional
    public Alert resolve(UUID id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Alert not found"));
        if (alert.getStatus() != AlertStatus.RESOLVED) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
        }
        return alert;
    }

    public record CreateAlertRequest(UUID zoneId, UUID deviceId, AlertSeverity severity, String message) {}
}
