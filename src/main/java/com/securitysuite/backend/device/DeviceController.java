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
    public List<DeviceDto> list(@RequestParam(required = false) UUID zoneId) {
        return deviceService.listAll(zoneId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<DeviceDto> create(@Valid @RequestBody DeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.create(request.name(), request.type(), request.zoneId()));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Update device heartbeat and status")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id, @Valid @RequestBody HeartbeatRequest request) {
        deviceService.updateStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(DeviceDto.from(deviceService.getById(UUID.fromString(id))));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable String id, @RequestBody DeviceDto request) {
        return ResponseEntity.ok(deviceService.updateDevice(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteDevice(@PathVariable String id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Map<String, Object>> unlock(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.unlockDevice(id));
    }

    public record DeviceRequest(@NotBlank String name, @NotNull DeviceType type, @NotNull UUID zoneId) {}
    public record HeartbeatRequest(@NotNull DeviceStatus status) {}
}
