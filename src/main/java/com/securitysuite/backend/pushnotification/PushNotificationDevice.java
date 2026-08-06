package com.securitysuite.backend.pushnotification;

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
public class PushNotificationDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500, unique = true)
    private String expoToken; // ExpoPushToken[xxxxxxxxxxxxxxxxxxxxxx]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceType deviceType;

    @Column(length = 100)
    private String deviceName; // e.g., "John's iPhone"

    @Column(nullable = false)
    private Instant registeredAt = Instant.now();

    @Column
    private Instant lastUsedAt;

    @Column(nullable = false)
    private Boolean active = true;
}
