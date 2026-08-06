package com.securitysuite.backend.pushnotification;

import java.time.Instant;
import java.util.UUID;

public record PushNotificationDeviceDto(
        UUID id,
        UUID userId,
        String userName,
        String expoToken,
        DeviceType deviceType,
        String deviceName,
        Instant registeredAt,
        Instant lastUsedAt,
        Boolean active
) {
    public static PushNotificationDeviceDto from(PushNotificationDevice device) {
        return new PushNotificationDeviceDto(
                device.getId(),
                device.getUser().getId(),
                device.getUser().getName(),
                device.getExpoToken(),
                device.getDeviceType(),
                device.getDeviceName(),
                device.getRegisteredAt(),
                device.getLastUsedAt(),
                device.getActive()
        );
    }
}
