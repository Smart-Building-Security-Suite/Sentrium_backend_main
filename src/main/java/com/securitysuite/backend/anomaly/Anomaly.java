package com.securitysuite.backend.anomaly;

import com.securitysuite.backend.user.User;
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
public class Anomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnomalySeverity severity;

    @Column(length = 50)
    private String entityType; // USER, DEVICE, ZONE

    @Column
    private UUID entityId;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String detailsJson; // Additional context as JSON

    @Column(nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(nullable = false)
    private Boolean reviewed = false;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column
    private Instant reviewedAt;

    @Column(nullable = false)
    private Boolean falsePositive = false;

    @Column(length = 500)
    private String actionTaken;

    @Column
    private Double confidenceScore; // 0.0 to 1.0 - AI confidence in detection
}
