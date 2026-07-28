package com.securitysuite.backend.zone;

import com.securitysuite.backend.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneService {
    private final ZoneRepository zoneRepository;

    public List<ZoneDto> listAll() {
        return zoneRepository.findAll().stream().map(ZoneDto::from).toList();
    }

    public Zone getById(UUID id) {
        return zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found"));
    }

    @Transactional
    public ZoneDto create(String name, String floor, String building) {
        Zone zone = new Zone();
        zone.setName(name);
        zone.setFloor(floor);
        zone.setBuilding(building);
        ZoneDto dto = ZoneDto.from(zoneRepository.save(zone));
        log.info("Zone created: {} (id={})", name, dto.id());
        return dto;
    }

    @Transactional
    public ZoneDto update(UUID id, String name, String floor, String building) {
        Zone zone = getById(id);
        zone.setName(name);
        zone.setFloor(floor);
        zone.setBuilding(building);
        return ZoneDto.from(zoneRepository.save(zone));
    }

    @Transactional
    public void delete(UUID id) {
        Zone zone = getById(id);
        zoneRepository.delete(zone);
        log.info("Zone deleted: id={}", id);
    }
}
