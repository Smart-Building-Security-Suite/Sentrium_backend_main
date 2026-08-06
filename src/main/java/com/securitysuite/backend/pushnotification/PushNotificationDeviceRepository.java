package com.securitysuite.backend.pushnotification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushNotificationDeviceRepository extends JpaRepository<PushNotificationDevice, UUID> {
    List<PushNotificationDevice> findByUserId(UUID userId);

    List<PushNotificationDevice> findByUserIdAndActiveTrue(UUID userId);

    Optional<PushNotificationDevice> findByExpoToken(String expoToken);

    @Query("SELECT d FROM PushNotificationDevice d WHERE d.user.id = :userId AND d.active = true")
    List<PushNotificationDevice> findActiveDevicesByUser(@Param("userId") UUID userId);

    @Query("SELECT d FROM PushNotificationDevice d WHERE d.user.role IN ('ADMIN', 'SECURITY_OFFICER') AND d.active = true")
    List<PushNotificationDevice> findAllSecurityPersonnelDevices();

    void deleteByExpoToken(String expoToken);
}
