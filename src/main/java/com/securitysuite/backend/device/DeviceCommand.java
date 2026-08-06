package com.securitysuite.backend.device;

import com.securitysuite.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_command")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "command_type", nullable = false)
    private String commandType; // UNLOCK, LOCK, REBOOT, etc.

    @Column(name = "command_payload", columnDefinition = "TEXT")
    private String commandPayload; // JSON

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CommandStatus status = CommandStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public enum CommandStatus {
        PENDING,
        SUCCESS,
        FAILED,
        TIMEOUT
    }
}
