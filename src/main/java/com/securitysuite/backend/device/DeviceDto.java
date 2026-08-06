package com.securitysuite.backend.device;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe API response for a Device — decouples the API contract from the JPA entity
 * and prevents N+1 lazy-loading of the nested Zone association on serialization.
 */
public record DeviceDto(
        UUID id,
        String name,
        DeviceType type,
        DeviceStatus status,
        UUID zoneId,
        String zoneName,
        Boolean active,
        Instant lastHeartbeatAt,

        // Connectivity fields
        String endpointUrl,
        String connectionProtocol,
        String connectionStatus,
        Instant lastCommandAt,

        // Camera stream fields
        String streamUrl,
        String streamType,
        String streamResolution,
        Integer streamFps
) {
    public static DeviceDto from(Device device) {
        return new DeviceDto(
                device.getId(),
                device.getName(),
                device.getType(),
                device.getStatus(),
                device.getZone().getId(),
                device.getZone().getName(),
                device.getActive(),
                device.getLastHeartbeatAt(),

                // Connectivity
                device.getEndpointUrl(),
                device.getConnectionProtocol(),
                device.getConnectionStatus(),
                device.getLastCommandAt(),

                // Camera stream
                device.getStreamUrl(),
                device.getStreamType(),
                device.getStreamResolution(),
                device.getStreamFps()
        );
    }
}
