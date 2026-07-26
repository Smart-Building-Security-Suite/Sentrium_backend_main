package com.securitysuite.backend.accesslog;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/access-logs")
@RequiredArgsConstructor
@Tag(name = "Access Logs")
public class AccessLogController {
    private final AccessLogRepository accessLogRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<AccessLogSummary> list(@RequestParam(required = false) UUID zoneId, @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 20);
        Page<AccessLog> result = zoneId == null ? accessLogRepository.findAll(pageable) : accessLogRepository.findByZoneId(zoneId, pageable);
        return result.map(AccessLogSummary::from);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<AccessLogSummary> create(@Valid @RequestBody AccessLogRequest request) {
        User user = userRepository.findById(request.userId()).orElseThrow(() -> new NotFoundException("User not found"));
        Device device = deviceRepository.findById(request.deviceId()).orElseThrow(() -> new NotFoundException("Device not found"));
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new NotFoundException("Zone not found"));
        if (!device.getZone().getId().equals(zone.getId())) {
            throw new IllegalArgumentException("zoneId: must match the selected device's zone");
        }
        AccessLog log = new AccessLog();
        log.setUser(user);
        log.setDevice(device);
        log.setZone(zone);
        log.setResult(request.result());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccessLogSummary.from(accessLogRepository.save(log)));
    }

    public record AccessLogRequest(@NotNull UUID userId, @NotNull UUID deviceId, @NotNull UUID zoneId, @NotNull AccessResult result) {}
}
