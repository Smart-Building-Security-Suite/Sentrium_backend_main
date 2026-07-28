package com.securitysuite.backend.device;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final ZoneService zoneService;

    public List<DeviceDto> listAll(UUID zoneId) {
        List<Device> devices = zoneId == null
                ? deviceRepository.findAll()
                : deviceRepository.findByZoneId(zoneId);
        return devices.stream().map(DeviceDto::from).toList();
    }

    public Device getById(UUID id) {
        return deviceRepository.findById(id).orElseThrow(() -> new NotFoundException("Device not found"));
    }

    @Transactional
    public DeviceDto create(String name, DeviceType type, UUID zoneId) {
        Zone zone = zoneService.getById(zoneId);
        Device device = new Device();
        device.setName(name);
        device.setType(type);
        device.setZone(zone);
        DeviceDto dto = DeviceDto.from(deviceRepository.save(device));
        log.info("Device created: {} (id={}, zone={})", name, dto.id(), zone.getName());
        return dto;
    }

    @Transactional
    public void updateStatus(UUID id, DeviceStatus status) {
        Device device = getById(id);
        device.setStatus(status);
        deviceRepository.save(device);
        log.info("Device heartbeat: id={}, status={}", id, status);
    }
}
