package com.securitysuite.backend.alert;

import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.zone.Zone;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    private Instant acknowledgedAt;

    private String acknowledgedBy;
}
