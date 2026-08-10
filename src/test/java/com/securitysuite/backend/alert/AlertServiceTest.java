package com.securitysuite.backend.alert;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.notification.AlertCreatedEvent;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.websocket.WebSocketAlertPublisher;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WebSocketAlertPublisher webSocketPublisher;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AlertService alertService;

    @Test
    @DisplayName("Create Alert - Publishes AlertCreatedEvent and saves Alert")
    void createAlertSuccess() {
        UUID zoneId = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setId(zoneId);
        zone.setName("Building A Floor 1");

        AlertService.CreateAlertRequest req = new AlertService.CreateAlertRequest(zoneId, null, AlertSeverity.CRITICAL, "Door Forced Open");

        given(zoneRepository.findById(zoneId)).willReturn(Optional.of(zone));
        given(alertRepository.save(any(Alert.class))).willAnswer(inv -> inv.getArgument(0));

        Alert alert = alertService.create(req);

        assertThat(alert.getZone()).isEqualTo(zone);
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);
        verify(alertRepository).save(any(Alert.class));
        verify(eventPublisher).publishEvent(any(AlertCreatedEvent.class));
    }

    @Test
    @DisplayName("Resolve Alert - Updates status to RESOLVED and sets resolvedAt timestamp")
    void resolveAlertSuccess() {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setStatus(AlertStatus.OPEN);

        given(alertRepository.findById(alertId)).willReturn(Optional.of(alert));

        Alert resolved = alertService.resolve(alertId);

        assertThat(resolved.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Resolve Alert - Throws NotFoundException for invalid ID")
    void resolveAlertNotFound() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(alertId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.resolve(alertId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Alert not found");
    }
}
