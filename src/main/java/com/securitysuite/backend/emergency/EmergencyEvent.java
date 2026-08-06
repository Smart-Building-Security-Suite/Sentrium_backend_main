package com.securitysuite.backend.emergency;

import com.securitysuite.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class EmergencyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmergencyEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmergencySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmergencyStatus status = EmergencyStatus.ACTIVE;

    @ManyToOne
    @JoinColumn(name = "triggered_by_user_id")
    private User triggeredBy;

    @Column(nullable = false)
    private Instant triggeredAt = Instant.now();

    @Column
    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String affectedZones; // JSON array of zone IDs

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String responseActions;

    @Column
    private Instant allClearAt;

    @ElementCollection
    @CollectionTable(name = "emergency_event_notifications", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "notified_user_id")
    private List<UUID> notifiedUsers = new ArrayList<>();
}
