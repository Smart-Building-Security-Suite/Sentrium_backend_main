package com.securitysuite.backend.device;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {
    Page<DeviceCommand> findByDeviceIdOrderByRequestedAtDesc(UUID deviceId, Pageable pageable);
    List<DeviceCommand> findByStatus(DeviceCommand.CommandStatus status);
}
