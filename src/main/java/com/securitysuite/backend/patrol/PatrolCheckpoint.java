package com.securitysuite.backend.patrol;

import com.securitysuite.backend.zone.Zone;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PatrolCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private PatrolRoute route;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String location;

    @ManyToOne
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(nullable = false)
    private Integer sequenceOrder;

    @Column(unique = true, length = 200)
    private String qrCode; // UUID-based QR code data

    @Column(nullable = false)
    private Boolean required = true;
}
