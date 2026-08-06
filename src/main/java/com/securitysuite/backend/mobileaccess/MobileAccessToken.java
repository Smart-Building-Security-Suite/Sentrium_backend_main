package com.securitysuite.backend.mobileaccess;

import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.user.User;
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
public class MobileAccessToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String qrCodeData; // UUID or encrypted token

    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device; // Specific door/device

    @ManyToOne
    @JoinColumn(name = "zone_id")
    private Zone zone; // Or any device in this zone

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Integer usesRemaining; // NULL = unlimited

    @Column(nullable = false)
    private Integer usedCount = 0;

    @Column
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Boolean revoked = false;

    @Column(length = 500)
    private String purpose; // e.g., "Visitor access for meeting"
}
