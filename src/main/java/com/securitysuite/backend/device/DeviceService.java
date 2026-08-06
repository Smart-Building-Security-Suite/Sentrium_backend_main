package com.securitysuite.backend.device;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final ZoneService zoneService;
    private final DeviceStatusHistoryRepository historyRepository;
    private final HttpDeviceCommandService commandService;

    public List<DeviceDto> listAll(UUID zoneId) {
        List<Device> devices = zoneId == null
                ? deviceRepository.findAll()
                : deviceRepository.findByZoneId(zoneId);
        // Filter to only show active devices by default
        return devices.stream()
                .filter(Device::getActive)
                .map(DeviceDto::from)
                .toList();
    }

    public Device getById(UUID id) {
        return deviceRepository.findById(id).orElseThrow(() -> new NotFoundException("Device not found"));
    }

    @Transactional
    public DeviceDto create(String name, DeviceType type, UUID zoneId) {
        Zone zone = zoneService.getById(zoneId);
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        device.setZone(zone);
        DeviceDto dto = DeviceDto.from(deviceRepository.save(device));
        log.info("Device created: {} (id={}, zone={})", name, dto.id(), zone.getName());
        return dto;
    }

    @Transactional
    public void updateStatus(UUID id, DeviceStatus status) {
        Device device = getById(id);
        DeviceStatus oldStatus = device.getStatus();
        device.setStatus(status);
        device.setLastHeartbeatAt(Instant.now());
        deviceRepository.save(device);

        // Record status change in history if status changed
        if (oldStatus != status) {
            DeviceStatusHistory history = new DeviceStatusHistory();
            history.setDevice(device);
            history.setStatus(status);
            history.setRecordedAt(Instant.now());
            history.setNotes("Status changed from " + oldStatus + " to " + status);
            historyRepository.save(history);
        }

        log.info("Device heartbeat: id={}, status={}", id, status);
    }

    @Transactional
    public DeviceDto updateDevice(String id, DeviceDto request) {
        Device device = getById(UUID.fromString(id));
        if (request.name() != null) {
            device.setName(request.name());
        }
        if (request.type() != null) {
            device.setType(request.type());
        }
        if (request.zoneId() != null) {
            device.setZone(zoneService.getById(request.zoneId()));
        }
        deviceRepository.save(device);
        log.info("Device updated: id={}", id);
        return DeviceDto.from(device);
    }

    @Transactional
    public void deleteDevice(String id) {
        Device device = getById(UUID.fromString(id));
        deviceRepository.delete(device);
        log.info("Device deleted: id={}", id);
    }

    @Transactional
    public Map<String, Object> unlockDevice(String id) {
        UUID deviceId = UUID.fromString(id);
        Device device = getById(deviceId);

        if (device.getType() != DeviceType.ACCESS_POINT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device is not an ACCESS_POINT");
        }

        String triggeredBy = "unknown";
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            triggeredBy = SecurityContextHolder.getContext().getAuthentication().getName();
        }

        // If device has endpoint configured, send actual command
        if (device.getEndpointUrl() != null && !device.getEndpointUrl().isBlank()) {
            HttpDeviceCommandService.DeviceCommandResponse response = commandService.unlockDevice(deviceId, 5);

            return Map.of(
                "id", id,
                "action", "UNLOCK",
                "triggeredBy", triggeredBy,
                "timestamp", Instant.now().toString(),
                "success", response.success(),
                "message", response.message(),
                "commandId", response.commandId() != null ? response.commandId().toString() : "N/A"
            );
        } else {
            // Legacy behavior - just log (no actual device control)
            log.info("Device unlocked (metadata only): id={}, triggeredBy={}", id, triggeredBy);
            return Map.of(
                "id", id,
                "action", "UNLOCK",
                "triggeredBy", triggeredBy,
                "timestamp", Instant.now().toString(),
                "warning", "Device endpoint not configured. This is a metadata-only operation."
            );
        }
    }

    public List<DeviceStatusHistoryDto> getDeviceHistory(UUID deviceId, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        return historyRepository.findByDeviceIdOrderByRecordedAtDesc(deviceId, pageable)
                .stream()
                .map(DeviceStatusHistoryDto::from)
                .toList();
    }

    @Transactional
    public DeviceDto deactivateDevice(String id) {
        Device device = getById(UUID.fromString(id));
        device.setActive(false);
        device.setDeactivatedAt(Instant.now());
        deviceRepository.save(device);
        log.info("Device soft-deleted: id={}", id);
        return DeviceDto.from(device);
    }

    @Transactional
    public DeviceDto configureDevice(String id, DeviceController.DeviceConfigRequest request) {
        Device device = getById(UUID.fromString(id));

        if (request.endpointUrl() != null) {
            device.setEndpointUrl(request.endpointUrl());
        }
        if (request.apiKey() != null) {
            // TODO: Encrypt the API key before storing
            device.setApiKeyEncrypted(request.apiKey());
        }
        if (request.connectionProtocol() != null) {
            device.setConnectionProtocol(request.connectionProtocol());
        }
        if (request.streamUrl() != null) {
            device.setStreamUrl(request.streamUrl());
        }
        if (request.streamType() != null) {
            device.setStreamType(request.streamType());
        }
        if (request.streamUsername() != null) {
            device.setStreamUsername(request.streamUsername());
        }
        if (request.streamPassword() != null) {
            // TODO: Encrypt the stream password before storing
            device.setStreamPasswordEncrypted(request.streamPassword());
        }
        if (request.streamResolution() != null) {
            device.setStreamResolution(request.streamResolution());
        }
        if (request.streamFps() != null) {
            device.setStreamFps(request.streamFps());
        }

        deviceRepository.save(device);
        log.info("Device configured: id={}", id);
        return DeviceDto.from(device);
    }

    @Transactional
    public Map<String, Object> lockDevice(String id) {
        UUID deviceId = UUID.fromString(id);
        Device device = getById(deviceId);

        if (device.getType() != DeviceType.ACCESS_POINT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device is not an ACCESS_POINT");
        }

        String triggeredBy = "unknown";
        if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
            triggeredBy = SecurityContextHolder.getContext().getAuthentication().getName();
        }

        if (device.getEndpointUrl() != null && !device.getEndpointUrl().isBlank()) {
            HttpDeviceCommandService.DeviceCommandResponse response = commandService.lockDevice(deviceId);

            return Map.of(
                "id", id,
                "action", "LOCK",
                "triggeredBy", triggeredBy,
                "timestamp", Instant.now().toString(),
                "success", response.success(),
                "message", response.message(),
                "commandId", response.commandId() != null ? response.commandId().toString() : "N/A"
            );
        } else {
            log.info("Device locked (metadata only): id={}, triggeredBy={}", id, triggeredBy);
            return Map.of(
                "id", id,
                "action", "LOCK",
                "triggeredBy", triggeredBy,
                "timestamp", Instant.now().toString(),
                "warning", "Device endpoint not configured. This is a metadata-only operation."
            );
        }
    }

    public java.util.List<HttpDeviceCommandService.DeviceCommandDto> getCommandHistory(UUID deviceId, int limit) {
        return commandService.getCommandHistory(deviceId, limit);
    }
}
