package com.securitysuite.backend.patrol;

import com.securitysuite.backend.incident.Incident;
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
public class PatrolCheckpointScan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PatrolSession session;

    @ManyToOne(optional = false)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private PatrolCheckpoint checkpoint;

    @Column(nullable = false)
    private Instant scannedAt = Instant.now();

    @Column(nullable = false)
    private Boolean incidentReported = false;

    @ManyToOne
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Column(length = 1000)
    private String notes;
}
