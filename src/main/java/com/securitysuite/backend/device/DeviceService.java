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
    private final com.securitysuite.backend.security.EncryptionService encryptionService;

    public List<DeviceDto> listAll(UUID zoneId, DeviceType type, DeviceStatus status, Boolean active) {
        List<Device> devices;

        // Apply filters based on what's provided
        if (zoneId != null && type != null && status != null) {
            devices = deviceRepository.findByZoneIdAndTypeAndStatus(zoneId, type, status);
        } else if (zoneId != null && type != null) {
            devices = deviceRepository.findByZoneIdAndType(zoneId, type);
        } else if (zoneId != null && status != null) {
            devices = deviceRepository.findByZoneIdAndStatus(zoneId, status);
        } else if (type != null && status != null) {
            devices = deviceRepository.findByTypeAndStatus(type, status);
        } else if (zoneId != null) {
            devices = deviceRepository.findByZoneId(zoneId);
        } else if (type != null) {
            devices = deviceRepository.findByType(type);
        } else if (status != null) {
            devices = deviceRepository.findByStatus(status);
        } else if (active != null) {
            devices = deviceRepository.findByActive(active);
        } else {
            devices = deviceRepository.findAll();
        }

        // Filter by active status if specified, otherwise default to showing only active devices
        final boolean showActive = active != null ? active : true;
        return devices.stream()
                .filter(device -> active == null ? device.getActive() : device.getActive() == showActive)
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
        try {
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
                try {
                    HttpDeviceCommandService.DeviceCommandResponse response = commandService.unlockDevice(deviceId, 5);

                    return Map.of(
                        "id", id,
                        "action", "UNLOCK",
                        "triggeredBy", triggeredBy,
                        "timestamp", Instant.now().toString(),
                        "success", response.success(),
                        "message", response.message() != null ? response.message() : "",
                        "commandId", response.commandId() != null ? response.commandId().toString() : "N/A"
                    );
                } catch (Exception e) {
                    log.error("Failed to execute unlock command on device {}: {}", id, e.getMessage(), e);
                    return Map.of(
                        "id", id,
                        "action", "UNLOCK",
                        "triggeredBy", triggeredBy,
                        "timestamp", Instant.now().toString(),
                        "success", false,
                        "message", "Command failed: " + e.getMessage()
                    );
                }
            } else {
                log.info("Device unlocked (metadata only): id={}, triggeredBy={}", id, triggeredBy);
                return Map.of(
                    "id", id,
                    "action", "UNLOCK",
                    "triggeredBy", triggeredBy,
                    "timestamp", Instant.now().toString(),
                    "success", true,
                    "message", "Device endpoint not configured. Command recorded but not sent to hardware."
                );
            }
        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException so it's handled properly by Spring
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in unlockDevice for id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to unlock device: " + e.getMessage(), e);
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
            // Encrypt API key before storing
            device.setApiKeyEncrypted(encryptionService.encrypt(request.apiKey()));
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
            // Encrypt stream password before storing
            device.setStreamPasswordEncrypted(encryptionService.encrypt(request.streamPassword()));
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
        try {
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
                try {
                    HttpDeviceCommandService.DeviceCommandResponse response = commandService.lockDevice(deviceId);

                    return Map.of(
                        "id", id,
                        "action", "LOCK",
                        "triggeredBy", triggeredBy,
                        "timestamp", Instant.now().toString(),
                        "success", response.success(),
                        "message", response.message() != null ? response.message() : "",
                        "commandId", response.commandId() != null ? response.commandId().toString() : "N/A"
                    );
                } catch (Exception e) {
                    log.error("Failed to execute lock command on device {}: {}", id, e.getMessage(), e);
                    return Map.of(
                        "id", id,
                        "action", "LOCK",
                        "triggeredBy", triggeredBy,
                        "timestamp", Instant.now().toString(),
                        "success", false,
                        "message", "Command failed: " + e.getMessage()
                    );
                }
            } else {
                log.info("Device locked (metadata only): id={}, triggeredBy={}", id, triggeredBy);
                return Map.of(
                    "id", id,
                    "action", "LOCK",
                    "triggeredBy", triggeredBy,
                    "timestamp", Instant.now().toString(),
                    "success", true,
                    "message", "Device endpoint not configured. Command recorded but not sent to hardware."
                );
            }
        } catch (ResponseStatusException e) {
            // Re-throw ResponseStatusException so it's handled properly by Spring
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in lockDevice for id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to lock device: " + e.getMessage(), e);
        }
    }

    public java.util.List<HttpDeviceCommandService.DeviceCommandDto> getCommandHistory(UUID deviceId, int limit) {
        return commandService.getCommandHistory(deviceId, limit);
    }
}
