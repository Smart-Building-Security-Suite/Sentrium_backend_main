package com.securitysuite.backend.patrol;

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
public class PatrolSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private PatrolRoute route;

    @ManyToOne(optional = false)
    @JoinColumn(name = "officer_user_id", nullable = false)
    private User officer;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    @Column
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatrolSessionStatus status = PatrolSessionStatus.IN_PROGRESS;

    @Column(length = 2000)
    private String notes;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PatrolCheckpointScan> scans = new ArrayList<>();
}
