package com.securitysuite.backend.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending HTTP commands to physical devices (smart locks, gates, etc.)
 */
@Service
@Slf4j
public class HttpDeviceCommandService {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public HttpDeviceCommandService(DeviceRepository deviceRepository,
                                   DeviceCommandRepository commandRepository,
                                   UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   ObjectMapper objectMapper,
                                   RestTemplateBuilder restTemplateBuilder) {
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send UNLOCK command to a door lock device
     */
    @Transactional
    public DeviceCommandResponse unlockDevice(UUID deviceId, Integer durationSeconds) {
        return sendCommand(deviceId, "UNLOCK", Map.of(
                "duration", durationSeconds != null ? durationSeconds : 5
        ));
    }

    /**
     * Send LOCK command to a door lock device
     */
    @Transactional
    public DeviceCommandResponse lockDevice(UUID deviceId) {
        return sendCommand(deviceId, "LOCK", Map.of());
    }

    /**
     * Send generic command to device
     */
    @Transactional
    public DeviceCommandResponse sendCommand(UUID deviceId, String commandType, Map<String, Object> payload) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device not found"));

        // Validate device type
        if (device.getType() != DeviceType.ACCESS_POINT) {
            throw new IllegalArgumentException("Device is not an ACCESS_POINT");
        }

        // Check if device has endpoint configured
        if (device.getEndpointUrl() == null || device.getEndpointUrl().isBlank()) {
            return DeviceCommandResponse.error(
                    "Device endpoint not configured. Please set endpoint_url for this device."
            );
        }

        // Get requesting user
        User requestedBy = getCurrentUser();

        // Create command record
        DeviceCommand command = new DeviceCommand();
        command.setDevice(device);
        command.setCommandType(commandType);
        command.setRequestedBy(requestedBy);
        command.setStatus(DeviceCommand.CommandStatus.PENDING);

        try {
            command.setCommandPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to serialize command payload", e);
            command.setCommandPayload(payload.toString());
        }

        try {
            command = commandRepository.save(command);
        } catch (Exception e) {
            log.error("Failed to persist command record for device {}: {}", deviceId, e.getMessage());
            return DeviceCommandResponse.error("Failed to record command: " + e.getMessage());
        }

        // Send HTTP request to device
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add device API key if configured
            if (device.getApiKeyEncrypted() != null && !device.getApiKeyEncrypted().isBlank()) {
                String apiKey = decryptApiKey(device.getApiKeyEncrypted());
                headers.set("X-API-Key", apiKey);
                headers.set("Authorization", "Bearer " + apiKey);
            }

            // Build request body
            Map<String, Object> requestBody = Map.of(
                    "command", commandType,
                    "deviceId", deviceId.toString(),
                    "timestamp", Instant.now().toString(),
                    "payload", payload
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Determine endpoint path based on command type
            String endpointPath = determineEndpointPath(commandType);
            String fullUrl = device.getEndpointUrl() + endpointPath;

            log.info("Sending {} command to device {} at {}", commandType, deviceId, fullUrl);

            // Send POST request
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);

            // Update command record with success
            command.setStatus(DeviceCommand.CommandStatus.SUCCESS);
            command.setExecutedAt(Instant.now());
            command.setResponsePayload(response.getBody());

            // Update device status
            device.setConnectionStatus("CONNECTED");
            device.setLastCommandAt(Instant.now());
            deviceRepository.save(device);

            commandRepository.save(command);

            log.info("Command {} executed successfully on device {}", commandType, deviceId);

            return DeviceCommandResponse.success(
                    command.getId(),
                    "Command executed successfully",
                    response.getBody()
            );

        } catch (Exception e) {
            // Update command record with failure
            command.setStatus(DeviceCommand.CommandStatus.FAILED);
            command.setExecutedAt(Instant.now());
            command.setErrorMessage(e.getMessage());

            // Update device status
            device.setConnectionStatus("ERROR");
            deviceRepository.save(device);

            commandRepository.save(command);

            log.error("Failed to send command to device {}: {}", deviceId, e.getMessage());

            return DeviceCommandResponse.error(
                    command.getId(),
                    "Failed to communicate with device: " + e.getMessage()
            );
        }
    }

    /**
     * Get command history for a device
     */
    public java.util.List<DeviceCommandDto> getCommandHistory(UUID deviceId, int limit) {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 100));

        return commandRepository.findByDeviceIdOrderByRequestedAtDesc(deviceId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String determineEndpointPath(String commandType) {
        return switch (commandType.toUpperCase()) {
            case "UNLOCK" -> "/unlock";
            case "LOCK" -> "/lock";
            case "REBOOT" -> "/reboot";
            case "STATUS" -> "/status";
            default -> "/command";
        };
    }

    private String decryptApiKey(String encrypted) {
        // TODO: Implement proper encryption/decryption
        // For now, assume it's stored in plain text (NOT SECURE)
        // In production, use AES encryption with a secret key
        return encrypted;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByPhoneNumber(username).orElse(null);
    }

    private DeviceCommandDto toDto(DeviceCommand command) {
        return new DeviceCommandDto(
                command.getId(),
                command.getDevice().getId(),
                command.getCommandType(),
                command.getStatus().name(),
                command.getRequestedBy() != null ? command.getRequestedBy().getName() : "System",
                command.getRequestedAt(),
                command.getExecutedAt(),
                command.getErrorMessage()
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record DeviceCommandResponse(
            boolean success,
            UUID commandId,
            String message,
            String deviceResponse
    ) {
        public static DeviceCommandResponse success(UUID commandId, String message, String deviceResponse) {
            return new DeviceCommandResponse(true, commandId, message, deviceResponse);
        }

        public static DeviceCommandResponse error(String message) {
            return new DeviceCommandResponse(false, null, message, null);
        }

        public static DeviceCommandResponse error(UUID commandId, String message) {
            return new DeviceCommandResponse(false, commandId, message, null);
        }
    }

    public record DeviceCommandDto(
            UUID id,
            UUID deviceId,
            String commandType,
            String status,
            String requestedBy,
            Instant requestedAt,
            Instant executedAt,
            String errorMessage
    ) {}
}
