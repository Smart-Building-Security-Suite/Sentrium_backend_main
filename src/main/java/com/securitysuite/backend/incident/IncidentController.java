package com.securitysuite.backend.incident;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident Management")
public class IncidentController {
    private final IncidentService incidentService;

    @GetMapping
    @Operation(summary = "List incidents with filtering",
               description = "Retrieves paginated incidents with optional filters by status, type, severity, zone, or assigned officer. Sorted by report date (newest first) by default. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<IncidentDto> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "reportedAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];

        if (!isValidSortField(sortField)) {
            sortField = "reportedAt";
        }

        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return incidentService.listAll(status, type, severity, zoneId, assignedToId, pageable);
    }

    private boolean isValidSortField(String field) {
        return field.matches("^(id|title|type|severity|status|location|reportedAt|resolvedAt|requiresFollowUp)$");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident details",
               description = "Retrieves complete details of a specific incident including timeline, assignments, and resolution. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<IncidentDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(incidentService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Report a new incident",
               description = "Creates a new security incident report with type, severity, location, and description. The reporting user is automatically recorded. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<IncidentDto> create(
            @Valid @RequestBody IncidentService.CreateIncidentRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.create(request, principal.getUsername()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update incident status",
               description = "Changes the incident status (OPEN, IN_PROGRESS, INVESTIGATING, RESOLVED, CLOSED, FALSE_ALARM). Updates the timeline. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<IncidentDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request.status()));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign incident to an officer",
               description = "Assigns the incident to a specific security officer for investigation and resolution. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<IncidentDto> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(incidentService.assign(id, request.assignedToId()));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an incident",
               description = "Marks the incident as resolved with a resolution summary and actions taken. Sets status to RESOLVED and records resolution time. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<IncidentDto> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request) {
        return ResponseEntity.ok(incidentService.resolve(id, request.resolution(), request.actionsTaken()));
    }

    @GetMapping("/open")
    @Operation(summary = "Get all open incidents",
               description = "Returns a list of all incidents with status OPEN, IN_PROGRESS, or INVESTIGATING. Used for dashboard views. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<IncidentDto>> getOpenIncidents() {
        return ResponseEntity.ok(incidentService.getOpenIncidents());
    }

    @GetMapping("/open/count")
    @Operation(summary = "Get count of open incidents",
               description = "Returns the total number of unresolved incidents. Used for dashboard KPIs. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OpenIncidentCountResponse> getOpenCount() {
        return ResponseEntity.ok(new OpenIncidentCountResponse(incidentService.getOpenIncidentCount()));
    }

    public record UpdateStatusRequest(@NotNull IncidentStatus status) {}
    public record AssignRequest(@NotNull UUID assignedToId) {}
    public record ResolveRequest(@NotBlank String resolution, String actionsTaken) {}
    public record OpenIncidentCountResponse(long count) {}
}
