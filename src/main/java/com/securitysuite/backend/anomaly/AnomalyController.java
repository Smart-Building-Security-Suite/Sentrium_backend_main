package com.securitysuite.backend.anomaly;

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
@RequestMapping("/anomalies")
@RequiredArgsConstructor
@Tag(name = "Anomaly Detection", description = "AI-powered security anomaly detection and analysis")
public class AnomalyController {
    private final AnomalyService anomalyService;

    @GetMapping
    @Operation(summary = "List anomalies with filtering",
               description = "Retrieves paginated list of AI-detected security anomalies with filters by type, severity, and review status. Sorted by detection time (newest first) by default. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<AnomalyDto> list(
            @RequestParam(required = false) AnomalyType type,
            @RequestParam(required = false) AnomalySeverity severity,
            @RequestParam(required = false) Boolean reviewed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return anomalyService.listAll(type, severity, reviewed, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get anomaly details",
               description = "Retrieves complete details of a specific AI-detected anomaly including confidence score, entity information, and review status. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AnomalyDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(anomalyService.getById(id));
    }

    @GetMapping("/unreviewed")
    @Operation(summary = "Get unreviewed anomalies (ordered by severity)",
               description = "Returns all anomalies that have not yet been reviewed by security personnel, sorted by severity (CRITICAL first). Used for triage queues. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<AnomalyDto>> getUnreviewed() {
        return ResponseEntity.ok(anomalyService.getUnreviewed());
    }

    @GetMapping("/unreviewed/count")
    @Operation(summary = "Count unreviewed anomalies",
               description = "Returns the number of anomalies awaiting review. Used for dashboard alerts. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreviewedCountResponse> getUnreviewedCount() {
        return ResponseEntity.ok(new UnreviewedCountResponse(anomalyService.countUnreviewed()));
    }

    @PatchMapping("/{id}/review")
    @Operation(summary = "Mark anomaly as reviewed",
               description = "Records that an anomaly has been reviewed by security personnel. Captures action taken and reviewer identity. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AnomalyDto> markReviewed(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewAnomalyRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(anomalyService.markReviewed(id, request.actionTaken(), principal.getUsername()));
    }

    @PatchMapping("/{id}/false-positive")
    @Operation(summary = "Mark anomaly as false positive",
               description = "Flags an anomaly as a false detection to improve AI model accuracy. Records who marked it and trains the system to reduce similar false positives. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AnomalyDto> markFalsePositive(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(anomalyService.markFalsePositive(id, principal.getUsername()));
    }

    @PostMapping
    @Operation(summary = "Manually create anomaly (for testing/manual detection)",
               description = "Manually creates an anomaly record for testing or when security personnel identify a pattern not caught by AI. Includes type, severity, entity details, and confidence score. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AnomalyDto> createAnomaly(@Valid @RequestBody CreateAnomalyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(anomalyService.createAnomaly(
                        request.anomalyType(),
                        request.severity(),
                        request.entityType(),
                        request.entityId(),
                        request.description(),
                        request.detailsJson(),
                        request.confidenceScore()
                ));
    }

    @PostMapping("/detect-now")
    @Operation(summary = "Trigger manual anomaly detection run",
               description = "Manually triggers the AI anomaly detection engine to analyze recent activity. Normally runs automatically on a schedule. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DetectionResponse> triggerDetection() {
        anomalyService.detectAnomalies();
        return ResponseEntity.ok(new DetectionResponse("Anomaly detection completed"));
    }

    // ===== DTOs =====
    public record ReviewAnomalyRequest(@NotBlank String actionTaken) {}

    public record CreateAnomalyRequest(
            @NotNull AnomalyType anomalyType,
            @NotNull AnomalySeverity severity,
            String entityType,
            UUID entityId,
            @NotBlank String description,
            String detailsJson,
            Double confidenceScore
    ) {}

    public record UnreviewedCountResponse(long count) {}
    public record DetectionResponse(String message) {}
}
