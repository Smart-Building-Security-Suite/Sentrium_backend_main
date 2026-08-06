package com.securitysuite.backend.device;

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
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status = DeviceStatus.IDLE;

    @Column
    private Instant lastHeartbeatAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private Instant deactivatedAt;

    // ── Device Connectivity ──────────────────────────────────────────────────
    @Column(name = "endpoint_url")
    private String endpointUrl; // HTTP endpoint for device commands

    @Column(name = "api_key_encrypted")
    private String apiKeyEncrypted; // Encrypted API key for device auth

    @Column(name = "connection_protocol")
    private String connectionProtocol = "HTTP"; // HTTP, MQTT, WEBSOCKET, NONE

    @Column(name = "connection_status")
    private String connectionStatus = "DISCONNECTED"; // CONNECTED, DISCONNECTED, ERROR

    @Column(name = "last_command_at")
    private Instant lastCommandAt;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    // ── Camera Stream Configuration ──────────────────────────────────────────
    @Column(name = "stream_url")
    private String streamUrl; // RTSP, HTTP, HLS stream URL

    @Column(name = "stream_type")
    private String streamType; // RTSP, HTTP, HLS, MJPEG

    @Column(name = "stream_username")
    private String streamUsername;

    @Column(name = "stream_password_encrypted")
    private String streamPasswordEncrypted;

    @Column(name = "stream_resolution")
    private String streamResolution; // 1080p, 720p, etc.

    @Column(name = "stream_fps")
    private Integer streamFps;
}
