package com.securitysuite.backend.surveillance;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "motion_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cameraId;

    @Column(nullable = false)
    private String cameraName;

    @Column(nullable = false)
    @Builder.Default
    private Instant detectedAt = Instant.now();

    @Column(nullable = false)
    private Double confidence;
}
