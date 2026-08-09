package com.securitysuite.backend.websocket;

import com.securitysuite.backend.alert.Alert;
import com.securitysuite.backend.notification.AlertCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAlertPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAlertCreated(AlertCreatedEvent event) {
        Alert alert = event.alert();
        AlertWebSocketMessage message = new AlertWebSocketMessage(
                "ALERT_CREATED",
                alert.getId(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getZone().getId(),
                alert.getZone().getName(),
                alert.getDevice() != null ? alert.getDevice().getId() : null,
                alert.getDevice() != null ? alert.getDevice().getName() : null,
                Instant.now()
        );

        // Broadcast to all connected clients subscribed to /topic/alerts
        messagingTemplate.convertAndSend("/topic/alerts", message);
        log.info("Broadcasted alert via WebSocket: alertId={}", alert.getId());
    }

    public void broadcastAlertUpdate(Alert alert, String eventType) {
        AlertWebSocketMessage message = new AlertWebSocketMessage(
                eventType,
                alert.getId(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getZone().getId(),
                alert.getZone().getName(),
                alert.getDevice() != null ? alert.getDevice().getId() : null,
                alert.getDevice() != null ? alert.getDevice().getName() : null,
                Instant.now()
        );

        messagingTemplate.convertAndSend("/topic/alerts", message);
        log.info("Broadcasted alert update via WebSocket: alertId={}, eventType={}", alert.getId(), eventType);
    }
}
