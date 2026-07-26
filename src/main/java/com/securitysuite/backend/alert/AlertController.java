package com.securitysuite.backend.alert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Alert> list(@RequestParam(required = false) AlertStatus status,
                            @RequestParam(required = false) AlertSeverity severity) {
        return alertService.list(status, severity);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Alert> create(@Valid @RequestBody AlertRequest request) {
        Alert alert = alertService.create(new AlertService.CreateAlertRequest(request.zoneId(), request.deviceId(), request.severity(), request.message()));
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Alert resolve(@PathVariable UUID id) {
        return alertService.resolve(id);
    }

    public record AlertRequest(@NotNull UUID zoneId, UUID deviceId, @NotNull AlertSeverity severity, @NotBlank String message) {}
}
