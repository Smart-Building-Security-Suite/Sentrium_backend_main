package com.securitysuite.backend.device;

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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "Devices")
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all devices",
               description = "Retrieves all security devices. Optionally filter by zoneId to get devices in a specific zone.")
    public List<DeviceDto> list(@RequestParam(required = false) UUID zoneId) {
        return deviceService.listAll(zoneId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    @Operation(summary = "Register a new device",
               description = "Creates a new security device (camera, access control, sensor, etc.) and associates it with a zone. Admin and Security Officer access.")
    public ResponseEntity<DeviceDto> create(@Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.create(request.name(), request.type(), request.zoneId()));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Update device heartbeat and status",
               description = "Records device health check and updates its operational status (ONLINE, OFFLINE, ERROR, MAINTENANCE). Used by device monitoring systems.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id, @Valid @RequestBody HeartbeatRequest request) {
        deviceService.updateStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get device by ID",
               description = "Retrieves detailed information about a specific device including its current status, zone, and configuration.")
    public ResponseEntity<DeviceDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.getById(UUID.fromString(id))));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    @Operation(summary = "Update device details",
               description = "Modifies device properties such as name, type, zone assignment, or configuration. Admin and Security Officer access.")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable String id, @RequestBody DeviceDto request) {
        return ResponseEntity.ok(deviceService.updateDevice(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Soft-delete device (preserves audit history)",
               description = "Deactivates a device without removing historical data. The device will no longer appear in active lists but its records remain for auditing. Admin only.")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<DeviceDto> deactivateDevice(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.deactivateDevice(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hard delete device (use with caution)",
               description = "Permanently removes a device and all its associated data. This action cannot be undone. Use deactivate for normal operations. Admin only.")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    @Operation(summary = "Remotely unlock an access control device",
               description = "Sends an unlock command to an access control device such as a door lock or gate. Returns operation status. Admin and Security Officer access.")
    public ResponseEntity<Map<String, Object>> unlock(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.unlockDevice(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get device status history timeline",
               description = "Retrieves the status change history for a device, showing when it went online/offline or had errors. Limited to 100 records. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<DeviceStatusHistoryDto>> getHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(deviceService.getDeviceHistory(UUID.fromString(id), Math.min(limit, 100)));
    }

    @PostMapping("/{id}/configure")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Configure device connectivity",
               description = "Set device endpoint URL, API key, stream URL for cameras. Required for physical device control. Admin only.")
    public ResponseEntity<DeviceDto> configureDevice(
            @PathVariable String id,
            @Valid @RequestBody DeviceConfigRequest request) {
        return ResponseEntity.ok(deviceService.configureDevice(id, request));
    }

    @GetMapping("/{id}/commands")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    @Operation(summary = "Get device command history",
               description = "Retrieves the history of commands sent to this device (unlock, lock, etc.) with execution status. Admin and Security Officer access.")
    public ResponseEntity<List<HttpDeviceCommandService.DeviceCommandDto>> getCommands(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(deviceService.getCommandHistory(UUID.fromString(id), limit));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    @Operation(summary = "Remotely lock an access control device",
               description = "Sends a lock command to an access control device. Admin and Security Officer access.")
    public ResponseEntity<Map<String, Object>> lock(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.lockDevice(id));
    }

    public record DeviceRequest(@NotBlank String name, @NotNull DeviceType type, @NotNull UUID zoneId) {}
    public record HeartbeatRequest(@NotNull DeviceStatus status) {}
    public record DeviceConfigRequest(
            String endpointUrl,
            String apiKey,
            String connectionProtocol,
            String streamUrl,
            String streamType,
            String streamUsername,
            String streamPassword,
            String streamResolution,
            Integer streamFps
    ) {}
}
