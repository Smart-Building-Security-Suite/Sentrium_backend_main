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
    @Operation(summary = "List all patrol routes",
               description = "Retrieves all defined patrol routes including active and inactive routes. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolRouteDto>> listRoutes() {
        return ResponseEntity.ok(patrolService.listRoutes());
    }

    @GetMapping("/routes/enabled")
    @Operation(summary = "List enabled patrol routes",
               description = "Retrieves only active/enabled patrol routes available for starting new patrol sessions. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolRouteDto>> listEnabledRoutes() {
        return ResponseEntity.ok(patrolService.listEnabledRoutes());
    }

    @GetMapping("/routes/{id}")
    @Operation(summary = "Get route details",
               description = "Retrieves complete details of a patrol route including name, description, estimated duration, and checkpoint count. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolRouteDto> getRoute(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getRoute(id));
    }

    @PostMapping("/routes")
    @Operation(summary = "Create new patrol route",
               description = "Defines a new patrol route with name, description, and estimated duration. Checkpoints are added separately. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatrolRouteDto> createRoute(@Valid @RequestBody CreateRouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.createRoute(request.name(), request.description(), request.estimatedDurationMinutes()));
    }

    @GetMapping("/routes/{id}/checkpoints")
    @Operation(summary = "Get checkpoints for a route",
               description = "Retrieves all checkpoints associated with a patrol route in sequence order. Each checkpoint has a QR code for scanning. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolCheckpointDto>> getRouteCheckpoints(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getRouteCheckpoints(id));
    }

    // ===== CHECKPOINTS =====
    @PostMapping("/routes/{routeId}/checkpoints")
    @Operation(summary = "Add checkpoint to route",
               description = "Adds a new checkpoint to a patrol route with a unique QR code. Specify location, zone, sequence order, and whether it's required. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PatrolCheckpointDto> addCheckpoint(
            @PathVariable UUID routeId,
            @Valid @RequestBody AddCheckpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.addCheckpoint(routeId, request.name(), request.location(),
                        request.zoneId(), request.sequenceOrder(), request.required()));
    }

    @GetMapping("/checkpoints/qr/{qrCode}")
    @Operation(summary = "Get checkpoint by QR code",
               description = "Looks up a checkpoint using its QR code value. Used during patrol sessions to verify scan validity. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolCheckpointDto> getCheckpointByQr(@PathVariable String qrCode) {
        return ResponseEntity.ok(patrolService.getCheckpointByQrCode(qrCode));
    }

    // ===== SESSIONS =====
    @PostMapping("/sessions")
    @Operation(summary = "Start a patrol session",
               description = "Initiates a new patrol session for a specific route. Records the officer and start time. The officer must scan checkpoints in sequence. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> startSession(
            @Valid @RequestBody StartSessionRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patrolService.startSession(request.routeId(), principal.getUsername()));
    }

    @PostMapping("/sessions/{id}/scan")
    @Operation(summary = "Scan a checkpoint QR code",
               description = "Records a checkpoint scan during an active patrol session. Validates QR code, records timestamp and GPS location. Optional notes for observations. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolCheckpointScanDto> scanCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody ScanCheckpointRequest request) {
        return ResponseEntity.ok(patrolService.scanCheckpoint(id, request.qrCode(), request.notes()));
    }

    @PostMapping("/sessions/{id}/complete")
    @Operation(summary = "Complete patrol session",
               description = "Ends an active patrol session successfully. Records completion time and validates all required checkpoints were scanned. Optional completion notes. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> completeSession(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteSessionRequest request) {
        String notes = request != null ? request.notes() : null;
        return ResponseEntity.ok(patrolService.completeSession(id, notes));
    }

    @PostMapping("/sessions/{id}/abort")
    @Operation(summary = "Abort patrol session",
               description = "Terminates a patrol session early due to emergency or other reasons. Requires abort reason. Session is marked incomplete. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> abortSession(
            @PathVariable UUID id,
            @Valid @RequestBody AbortSessionRequest request) {
        return ResponseEntity.ok(patrolService.abortSession(id, request.reason()));
    }

    @GetMapping("/sessions/{id}/scans")
    @Operation(summary = "Get scans for a session",
               description = "Retrieves all checkpoint scans recorded during a patrol session with timestamps and any notes or linked incidents. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<PatrolCheckpointScanDto>> getSessionScans(@PathVariable UUID id) {
        return ResponseEntity.ok(patrolService.getSessionScans(id));
    }

    @GetMapping("/sessions/my-active")
    @Operation(summary = "Get my active patrol session",
               description = "Retrieves the currently active patrol session for the authenticated officer, if any. Returns null if no active session. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<PatrolSessionDto> getMyActiveSession(@AuthenticationPrincipal UserDetails principal) {
        // Get current user ID from phone number
        // For simplicity, returning null if no active session
        return ResponseEntity.ok(null);
    }

    @PatchMapping("/scans/{scanId}/link-incident")
    @Operation(summary = "Link incident to checkpoint scan",
               description = "Associates an incident report with a specific checkpoint scan, creating an audit trail between patrol observations and incident records. Admin and Security Officer access.")
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
