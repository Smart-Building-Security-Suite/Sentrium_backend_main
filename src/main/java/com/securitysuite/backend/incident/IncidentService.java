package com.securitysuite.backend.incident;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.pushnotification.PushNotificationService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public Page<IncidentDto> listAll(IncidentStatus status, IncidentType type, IncidentSeverity severity,
                                     UUID zoneId, UUID assignedToId, Pageable pageable) {
        try {
            if (status != null) return incidentRepository.findByStatus(status, pageable).map(IncidentDto::from);
            if (type != null) return incidentRepository.findByType(type, pageable).map(IncidentDto::from);
            if (severity != null) return incidentRepository.findBySeverity(severity, pageable).map(IncidentDto::from);
            if (zoneId != null) return incidentRepository.findByZoneId(zoneId, pageable).map(IncidentDto::from);
            if (assignedToId != null) return incidentRepository.findByAssignedToId(assignedToId, pageable).map(IncidentDto::from);

            return incidentRepository.findAll(pageable).map(IncidentDto::from);
        } catch (Exception e) {
            log.error("Error listing incidents: status={}, type={}, severity={}, zoneId={}, assignedToId={}",
                     status, type, severity, zoneId, assignedToId, e);
            throw new RuntimeException("Failed to list incidents: " + e.getMessage(), e);
        }
    }

    @Transactional
    public IncidentDto create(CreateIncidentRequest request, String reportedByPhoneNumber) {
        User reportedBy = userRepository.findByPhoneNumber(reportedByPhoneNumber)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Incident incident = new Incident();
        incident.setTitle(request.title());
        incident.setDescription(request.description());
        incident.setType(request.type());
        incident.setSeverity(request.severity());
        incident.setLocation(request.location());
        incident.setReportedBy(reportedBy);
        incident.setOccurredAt(request.occurredAt() != null ? request.occurredAt() : Instant.now());

        if (request.zoneId() != null) {
            Zone zone = zoneRepository.findById(request.zoneId())
                    .orElseThrow(() -> new NotFoundException("Zone not found"));
            incident.setZone(zone);
        }

        if (request.assignedToId() != null) {
            User assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new NotFoundException("Assigned user not found"));
            incident.setAssignedTo(assignedTo);
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }

        incident = incidentRepository.save(incident);
        log.info("Incident created: {} by {}", incident.getTitle(), reportedBy.getName());

        // 🔔 PUSH NOTIFICATION: New Incident (HIGH/CRITICAL severity only)
        if (pushNotificationService != null &&
            (incident.getSeverity() == IncidentSeverity.HIGH || incident.getSeverity() == IncidentSeverity.CRITICAL)) {
            pushNotificationService.sendToSecurityPersonnel(
                "🚨 New " + incident.getSeverity() + " Incident",
                incident.getTitle(),
                Map.of(
                    "incidentId", incident.getId().toString(),
                    "type", incident.getType().name(),
                    "severity", incident.getSeverity().name(),
                    "reportedBy", reportedBy.getName()
                )
            );
        }

        return IncidentDto.from(incident);
    }

    @Transactional
    public IncidentDto updateStatus(UUID id, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        incident.setStatus(newStatus);

        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
            incident.setResolvedAt(Instant.now());
        }

        return IncidentDto.from(incidentRepository.save(incident));
    }

    @Transactional
    public IncidentDto assign(UUID id, UUID assignedToId) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        User assignedTo = userRepository.findById(assignedToId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        incident.setAssignedTo(assignedTo);
        if (incident.getStatus() == IncidentStatus.OPEN) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }

        incident = incidentRepository.save(incident);

        // 🔔 PUSH NOTIFICATION: Incident Assigned to Officer
        if (pushNotificationService != null) {
            pushNotificationService.sendToUser(
                assignedToId,
                "📋 Incident Assigned to You",
                "Incident #" + incident.getId().toString().substring(0, 8) + ": " + incident.getTitle(),
                Map.of(
                    "incidentId", incident.getId().toString(),
                    "type", incident.getType().name(),
                    "severity", incident.getSeverity().name(),
                    "status", "ASSIGNED"
                )
            );
        }

        return IncidentDto.from(incident);
    }

    @Transactional
    public IncidentDto resolve(UUID id, String resolution, String actionsTaken) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolution(resolution);
        incident.setActionsTaken(actionsTaken);
        incident.setResolvedAt(Instant.now());

        return IncidentDto.from(incidentRepository.save(incident));
    }

    public IncidentDto getById(UUID id) {
        return IncidentDto.from(incidentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Incident not found")));
    }

    public List<IncidentDto> getOpenIncidents() {
        return incidentRepository.findOpenIncidents().stream()
                .map(IncidentDto::from)
                .toList();
    }

    public long getOpenIncidentCount() {
        return incidentRepository.countOpenIncidents();
    }

    public record CreateIncidentRequest(
            String title,
            String description,
            IncidentType type,
            IncidentSeverity severity,
            UUID zoneId,
            String location,
            Instant occurredAt,
            UUID assignedToId
    ) {}
}
