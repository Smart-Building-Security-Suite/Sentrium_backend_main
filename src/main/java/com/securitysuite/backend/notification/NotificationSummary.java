package com.securitysuite.backend.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationSummary(
        UUID id,
        UUID userId,
        UUID alertId,
        NotificationChannel channel,
        Instant readAt,
        Instant createdAt,
        String message,
        String zoneName,
        String severity
) {
    public static NotificationSummary from(Notification notification) {
        return new NotificationSummary(
                notification.getId(),
                notification.getUser().getId(),
                notification.getAlert() == null ? null : notification.getAlert().getId(),
                notification.getChannel(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getAlert() == null ? null : notification.getAlert().getMessage(),
                notification.getAlert() == null ? null : notification.getAlert().getZone().getName(),
                notification.getAlert() == null ? null : notification.getAlert().getSeverity().name()
        );
    }
}
