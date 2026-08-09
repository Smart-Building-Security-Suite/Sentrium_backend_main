package com.securitysuite.backend.patrol;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.incident.Incident;
import com.securitysuite.backend.incident.IncidentRepository;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatrolService {
    private final PatrolRouteRepository routeRepository;
    private final PatrolCheckpointRepository checkpointRepository;
    private final PatrolSessionRepository sessionRepository;
    private final PatrolCheckpointScanRepository scanRepository;
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final IncidentRepository incidentRepository;

    // ===== ROUTES =====
    @Transactional(readOnly = true)
    public List<PatrolRouteDto> listRoutes() {
        return routeRepository.findAllWithCheckpoints().stream()
                .map(PatrolRouteDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PatrolRouteDto> listEnabledRoutes() {
        return routeRepository.findByEnabledTrue().stream()
                .map(PatrolRouteDto::from)
                .toList();
    }

    @Transactional
    public PatrolRouteDto createRoute(String name, String description, Integer estimatedDurationMinutes) {
        PatrolRoute route = new PatrolRoute();
        route.setName(name);
        route.setDescription(description);
        route.setEstimatedDurationMinutes(estimatedDurationMinutes);
        route = routeRepository.save(route);
        log.info("Patrol route created: {}", name);
        return PatrolRouteDto.from(route);
    }

    @Transactional(readOnly = true)
    public PatrolRouteDto getRoute(UUID id) {
        return PatrolRouteDto.from(routeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patrol route not found")));
    }

    // ===== CHECKPOINTS =====
    @Transactional
    public PatrolCheckpointDto addCheckpoint(UUID routeId, String name, String location,
                                             UUID zoneId, Integer sequenceOrder, Boolean required) {
        PatrolRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new NotFoundException("Patrol route not found"));

        Zone zone = null;
        if (zoneId != null) {
            zone = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new NotFoundException("Zone not found"));
        }

        PatrolCheckpoint checkpoint = new PatrolCheckpoint();
        checkpoint.setRoute(route);
        checkpoint.setName(name);
        checkpoint.setLocation(location);
        checkpoint.setZone(zone);
        checkpoint.setSequenceOrder(sequenceOrder);
        checkpoint.setRequired(required != null ? required : true);
        checkpoint.setQrCode("CP-" + UUID.randomUUID().toString());

        checkpoint = checkpointRepository.save(checkpoint);
        log.info("Checkpoint added to route {}: {}", route.getName(), name);

        return PatrolCheckpointDto.from(checkpoint);
    }

    @Transactional(readOnly = true)
    public List<PatrolCheckpointDto> getRouteCheckpoints(UUID routeId) {
        PatrolRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new NotFoundException("Patrol route not found"));
        return route.getCheckpoints().stream()
                .map(PatrolCheckpointDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatrolCheckpointDto getCheckpointByQrCode(String qrCode) {
        return PatrolCheckpointDto.from(checkpointRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new NotFoundException("Checkpoint not found")));
    }

    // ===== SESSIONS =====
    @Transactional
    public PatrolSessionDto startSession(UUID routeId, String officerPhoneNumber) {
        PatrolRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new NotFoundException("Patrol route not found"));

        User officer = userRepository.findByPhoneNumber(officerPhoneNumber)
                .orElseThrow(() -> new NotFoundException("Officer not found"));

        // Check if officer already has active session
        sessionRepository.findActiveSessionByOfficer(officer.getId(), PatrolSessionStatus.IN_PROGRESS)
                .ifPresent(s -> {
                    throw new IllegalStateException("Officer already has an active patrol session");
                });

        PatrolSession session = new PatrolSession();
        session.setRoute(route);
        session.setOfficer(officer);
        session = sessionRepository.save(session);

        log.info("Patrol session started: officer={}, route={}", officer.getName(), route.getName());
        return PatrolSessionDto.from(session);
    }

    @Transactional
    public PatrolCheckpointScanDto scanCheckpoint(UUID sessionId, String qrCode, String notes) {
        PatrolSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Patrol session not found"));

        if (session.getStatus() != PatrolSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        PatrolCheckpoint checkpoint = checkpointRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new NotFoundException("Invalid QR code"));

        if (!checkpoint.getRoute().getId().equals(session.getRoute().getId())) {
            throw new IllegalStateException("Checkpoint does not belong to this route");
        }

        // Check if already scanned
        boolean alreadyScanned = session.getScans().stream()
                .anyMatch(s -> s.getCheckpoint().getId().equals(checkpoint.getId()));

        if (alreadyScanned) {
            throw new IllegalStateException("Checkpoint already scanned in this session");
        }

        PatrolCheckpointScan scan = new PatrolCheckpointScan();
        scan.setSession(session);
        scan.setCheckpoint(checkpoint);
        scan.setNotes(notes);
        scan = scanRepository.save(scan);

        log.info("Checkpoint scanned: session={}, checkpoint={}", sessionId, checkpoint.getName());
        return PatrolCheckpointScanDto.from(scan);
    }

    @Transactional
    public PatrolCheckpointScanDto linkIncidentToScan(UUID scanId, UUID incidentId) {
        PatrolCheckpointScan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException("Scan not found"));

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        scan.setIncidentReported(true);
        scan.setIncident(incident);
        scan = scanRepository.save(scan);

        return PatrolCheckpointScanDto.from(scan);
    }

    @Transactional
    public PatrolSessionDto completeSession(UUID sessionId, String notes) {
        PatrolSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Patrol session not found"));

        if (session.getStatus() != PatrolSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        session.setStatus(PatrolSessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setNotes(notes);
        session = sessionRepository.save(session);

        log.info("Patrol session completed: id={}, officer={}", sessionId, session.getOfficer().getName());
        return PatrolSessionDto.from(session);
    }

    @Transactional
    public PatrolSessionDto abortSession(UUID sessionId, String reason) {
        PatrolSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Patrol session not found"));

        session.setStatus(PatrolSessionStatus.ABORTED);
        session.setCompletedAt(Instant.now());
        session.setNotes("ABORTED: " + reason);
        session = sessionRepository.save(session);

        log.warn("Patrol session aborted: id={}, reason={}", sessionId, reason);
        return PatrolSessionDto.from(session);
    }

    @Transactional(readOnly = true)
    public List<PatrolSessionDto> getOfficerSessions(UUID officerId) {
        return sessionRepository.findByOfficerId(officerId).stream()
                .map(PatrolSessionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatrolSessionDto getActiveSessionForOfficer(UUID officerId) {
        return sessionRepository.findActiveSessionByOfficer(officerId, PatrolSessionStatus.IN_PROGRESS)
                .map(PatrolSessionDto::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PatrolCheckpointScanDto> getSessionScans(UUID sessionId) {
        return scanRepository.findBySessionId(sessionId).stream()
                .map(PatrolCheckpointScanDto::from)
                .toList();
    }
}
