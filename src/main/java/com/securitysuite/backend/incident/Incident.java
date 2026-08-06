package com.securitysuite.backend.incident;

import com.securitysuite.backend.user.User;
import com.securitysuite.backend.zone.Zone;
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
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(length = 200)
    private String location; // Specific location within zone

    @ManyToOne(optional = false)
    @JoinColumn(name = "reported_by_user_id", nullable = false)
    private User reportedBy;

    @Column(nullable = false)
    private Instant reportedAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column
    private Instant occurredAt; // When incident actually happened

    @Column
    private Instant resolvedAt;

    @Column(length = 2000)
    private String resolution;

    @ElementCollection
    @CollectionTable(name = "incident_evidence", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "file_url", length = 500)
    private List<String> evidenceUrls = new ArrayList<>(); // Photos, videos, documents

    @ElementCollection
    @CollectionTable(name = "incident_involved_parties", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "party_name", length = 200)
    private List<String> involvedParties = new ArrayList<>(); // Names of people involved

    @Column(length = 2000)
    private String actionsTaken;

    @Column(nullable = false)
    private Boolean requiresFollowUp = false;

    @Column
    private Instant followUpDate;

    @Column(length = 500)
    private String tags; // Comma-separated tags for categorization
}
