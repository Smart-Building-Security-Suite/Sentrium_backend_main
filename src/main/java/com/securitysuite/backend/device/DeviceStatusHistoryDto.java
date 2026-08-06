package com.securitysuite.backend.device;

import java.time.Instant;
import java.util.UUID;

public record DeviceStatusHistoryDto(
        UUID id,
        DeviceStatus status,
        Instant recordedAt,
        String notes
) {
    public static DeviceStatusHistoryDto from(DeviceStatusHistory history) {
        return new DeviceStatusHistoryDto(
                history.getId(),
                history.getStatus(),
                history.getRecordedAt(),
                history.getNotes()
        );
    }
}
