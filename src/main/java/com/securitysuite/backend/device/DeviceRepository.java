package com.securitysuite.backend.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByZoneId(UUID zoneId);
    List<Device> findByType(DeviceType type);
    List<Device> findByStatus(DeviceStatus status);
    List<Device> findByActive(Boolean active);
    List<Device> findByZoneIdAndType(UUID zoneId, DeviceType type);
    List<Device> findByZoneIdAndStatus(UUID zoneId, DeviceStatus status);
    List<Device> findByTypeAndStatus(DeviceType type, DeviceStatus status);
    List<Device> findByZoneIdAndTypeAndStatus(UUID zoneId, DeviceType type, DeviceStatus status);
}
