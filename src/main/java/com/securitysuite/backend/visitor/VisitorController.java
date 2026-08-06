package com.securitysuite.backend.visitor;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/visitors")
@RequiredArgsConstructor
@Tag(name = "Visitor Management")
public class VisitorController {
    private final VisitorService visitorService;

    @GetMapping
    @Operation(summary = "List visitors with optional filtering",
               description = "Retrieves paginated list of visitors with optional filters by status and host. Sorted by creation date (newest first) by default. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<VisitorDto> list(
            @RequestParam(required = false) VisitorStatus status,
            @RequestParam(required = false) UUID hostId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return visitorService.listAll(status, hostId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get visitor details",
               description = "Retrieves complete information about a specific visitor including check-in/out times, host, and status. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VisitorDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(visitorService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Pre-register a visitor",
               description = "Creates a visitor record in advance of their arrival. Records visitor name, purpose, host, and expected arrival time. Status set to PRE_REGISTERED. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VisitorDto> preRegister(
            @Valid @RequestBody VisitorService.CreateVisitorRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visitorService.preRegister(request, principal.getUsername()));
    }

    @PostMapping("/{id}/check-in")
    @Operation(summary = "Check in a visitor",
               description = "Records visitor arrival at the facility. Issues a visitor badge and updates status to CHECKED_IN. Captures check-in time. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VisitorDto> checkIn(
            @PathVariable UUID id,
            @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(visitorService.checkIn(id, request.badgeNumber()));
    }

    @PostMapping("/{id}/check-out")
    @Operation(summary = "Check out a visitor",
               description = "Records visitor departure from the facility. Updates status to CHECKED_OUT and captures checkout time. Badge should be returned. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VisitorDto> checkOut(@PathVariable UUID id) {
        return ResponseEntity.ok(visitorService.checkOut(id));
    }

    @GetMapping("/current")
    @Operation(summary = "Get all currently on-premises visitors",
               description = "Returns a list of all visitors currently checked in at the facility. Used for emergency roll call and occupancy monitoring. Admin, Security Officer, and Viewer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER','VIEWER')")
    public ResponseEntity<List<VisitorDto>> getCurrentVisitors() {
        return ResponseEntity.ok(visitorService.getCurrentVisitors());
    }

    @GetMapping("/current/count")
    @Operation(summary = "Get count of current visitors",
               description = "Returns the number of visitors currently on premises. Used for dashboard KPIs. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentVisitorCountResponse> getCurrentCount() {
        return ResponseEntity.ok(new CurrentVisitorCountResponse(visitorService.getCurrentVisitorCount()));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pre-registered visitor",
               description = "Cancels a pre-registered visitor entry if they will not be arriving. Updates status to CANCELLED. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VisitorDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(visitorService.cancel(id));
    }

    public record CheckInRequest(@NotBlank String badgeNumber) {}
    public record CurrentVisitorCountResponse(long count) {}
}
