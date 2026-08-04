package com.securitysuite.backend.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts")
public class AlertController {
    private final AlertService alertService;

    /**
     * Lists alerts with optional filtering. Paginated (default page size 20) to prevent OOM on large datasets.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<AlertDto> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Alert> results = alertService.list(status, severity);
        // Apply pagination in-memory over the filtered results.
        // TODO: push filters + pagination into a repository query for large datasets.
        int start = Math.min(page * size, results.size());
        int end   = Math.min(start + size, results.size());
        List<AlertDto> dtos = results.subList(start, end).stream().map(AlertDto::from).toList();
        return new PageImpl<>(dtos, PageRequest.of(page, size), results.size());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AlertDto> create(@Valid @RequestBody AlertRequest request) {
        Alert alert = alertService.create(
                new AlertService.CreateAlertRequest(request.zoneId(), request.deviceId(), request.severity(), request.message()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertDto.from(alert));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public AlertDto get(@PathVariable UUID id) {
        return AlertDto.from(alertService.get(id));
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public AlertDto acknowledge(@PathVariable UUID id) {
        return AlertDto.from(alertService.acknowledge(id));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public AlertDto resolve(@PathVariable UUID id) {
        return AlertDto.from(alertService.resolve(id));
    }

    public record AlertRequest(@NotNull UUID zoneId, UUID deviceId, @NotNull AlertSeverity severity, @NotBlank String message) {}
}
