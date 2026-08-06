package com.securitysuite.backend.device;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, UUID> {
    List<DeviceStatusHistory> findByDeviceIdOrderByRecordedAtDesc(UUID deviceId, Pageable pageable);
}
