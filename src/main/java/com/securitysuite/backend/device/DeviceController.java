package com.securitysuite.backend.device;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
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
@RequestMapping("/devices")
@RequiredArgsConstructor
@Tag(name = "Devices")
public class DeviceController {
    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;

    @GetMapping
    public List<Device> list(@RequestParam(required = false) UUID zoneId) {
        return zoneId == null ? deviceRepository.findAll() : deviceRepository.findByZoneId(zoneId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Device> create(@Valid @RequestBody DeviceRequest request) {
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new NotFoundException("Zone not found"));
        Device device = new Device();
        device.setName(request.name());
        device.setType(request.type());
        device.setZone(zone);
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceRepository.save(device));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Update device heartbeat and status")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id, @Valid @RequestBody HeartbeatRequest request) {
        Device device = deviceRepository.findById(id).orElseThrow(() -> new NotFoundException("Device not found"));
        device.setStatus(request.status());
        deviceRepository.save(device);
        return ResponseEntity.noContent().build();
    }

    public record DeviceRequest(@NotBlank String name, @NotNull DeviceType type, @NotNull UUID zoneId) {}
    public record HeartbeatRequest(@NotNull DeviceStatus status) {}
}
