package com.securitysuite.backend.zone;

import com.securitysuite.backend.common.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/zones")
@RequiredArgsConstructor
@Tag(name = "Zones")
public class ZoneController {
    private final ZoneRepository zoneRepository;

    @GetMapping
    public List<Zone> list() {
        return zoneRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Zone> create(@Valid @RequestBody ZoneRequest request) {
        Zone zone = new Zone();
        copy(request, zone);
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneRepository.save(zone));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Zone update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found"));
        copy(request, zone);
        return zoneRepository.save(zone);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Zone zone = zoneRepository.findById(id).orElseThrow(() -> new NotFoundException("Zone not found"));
        zoneRepository.delete(zone);
        return ResponseEntity.noContent().build();
    }

    private void copy(ZoneRequest request, Zone zone) {
        zone.setName(request.name());
        zone.setFloor(request.floor());
        zone.setBuilding(request.building());
    }

    public record ZoneRequest(@NotBlank String name, @NotBlank String floor, @NotBlank String building) {}
}
