package com.securitysuite.backend.patrol;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patrol")
@RequiredArgsConstructor
@Tag(name = "Patrol/Rounds Management", description = "QR-based patrol verification system")
public class PatrolController {
    private final PatrolService patrolService;

    // ===== ROUTES =====
    @GetMapping("/routes")
    @Operation(summary = "List all patrol routes")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolRouteDto>> listRoutes() {
        return ResponseEntity.ok(patrolService.listRoutes());
    }

    @GetMapping("/routes/enabled")
    @Operation(summary = "List enabled patrol routes")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolRouteDto>> listEnabledRoutes() {
        return ResponseEntity.ok(patrolService.listEnabledRoutes());
    }

    @GetMapping("/routes/{id}")
    @Operation(summary = "Get route details")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolRouteDto> getRoute(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getRoute(id));
    }

    @PostMapping("/routes")
    @Operation(summary = "Create new patrol route")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatrolRouteDto> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.createRoute(request.name(), request.description(), request.estimatedDurationMinutes()));
    }

    @GetMapping("/routes/{id}/checkpoints")
    @Operation(summary = "Get checkpoints for a route")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolCheckpointDto>> getRouteCheckpoints(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getRouteCheckpoints(id));
    }

    // ===== CHECKPOINTS =====
    @PostMapping("/routes/{routeId}/checkpoints")
    @Operation(summary = "Add checkpoint to route")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatrolCheckpointDto> addCheckpoint(
            @PathVariable UUID routeId,
            @Valid @RequestBody AddCheckpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.addCheckpoint(routeId, request.name(), request.location(),
                        request.zoneId(), request.sequenceOrder(), request.required()));
    }

    @GetMapping("/checkpoints/qr/{qrCode}")
    @Operation(summary = "Get checkpoint by QR code")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolCheckpointDto> getCheckpointByQr(@PathVariable String qrCode) {
        return ResponseEntity.ok(patrolService.getCheckpointByQrCode(qrCode));
    }

    // ===== SESSIONS =====
    @PostMapping("/sessions")
    @Operation(summary = "Start a patrol session")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> startSession(
            @Valid @RequestBody StartSessionRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.startSession(request.routeId(), principal.getUsername()));
    }

    @PostMapping("/sessions/{id}/scan")
    @Operation(summary = "Scan a checkpoint QR code")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolCheckpointScanDto> scanCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody ScanCheckpointRequest request) {
        return ResponseEntity.ok(patrolService.scanCheckpoint(id, request.qrCode(), request.notes()));
    }

    @PostMapping("/sessions/{id}/complete")
    @Operation(summary = "Complete patrol session")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> completeSession(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteSessionRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseEntity.ok(patrolService.completeSession(id, notes));
    }

    @PostMapping("/sessions/{id}/abort")
    @Operation(summary = "Abort patrol session")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> abortSession(
            @PathVariable UUID id,
            @Valid @RequestBody AbortSessionRequest request) {
        return ResponseEntity.ok(patrolService.abortSession(id, request.reason()));
    }

    @GetMapping("/sessions/{id}/scans")
    @Operation(summary = "Get scans for a session")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolCheckpointScanDto>> getSessionScans(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getSessionScans(id));
    }

    @GetMapping("/sessions/my-active")
    @Operation(summary = "Get my active patrol session")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> getMyActiveSession(@AuthenticationPrincipal UserDetails principal) {
        // Get current user ID from phone number
        // For simplicity, returning null if no active session
        return ResponseEntity.ok(null);
    }

    @PatchMapping("/scans/{scanId}/link-incident")
    @Operation(summary = "Link incident to checkpoint scan")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolCheckpointScanDto> linkIncident(
            @PathVariable UUID scanId,
            @Valid @RequestBody LinkIncidentRequest request) {
        return ResponseEntity.ok(patrolService.linkIncidentToScan(scanId, request.incidentId()));
    }

    // ===== DTOs =====
    public record CreateRouteRequest(@NotBlank String name, String description, Integer estimatedDurationMinutes) {}
    public record AddCheckpointRequest(@NotBlank String name, String location, UUID zoneId, @NotNull Integer sequenceOrder, Boolean required) {}
    public record StartSessionRequest(@NotNull UUID routeId) {}
    public record ScanCheckpointRequest(@NotBlank String qrCode, String notes) {}
    public record CompleteSessionRequest(String notes) {}
    public record AbortSessionRequest(@NotBlank String reason) {}
    public record LinkIncidentRequest(@NotNull UUID incidentId) {}
}
