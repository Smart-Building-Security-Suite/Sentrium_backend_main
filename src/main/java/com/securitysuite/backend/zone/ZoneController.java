package com.securitysuite.backend.zone;

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
    private final ZoneService zoneService;

    @GetMapping
    public List<ZoneDto> list() {
        return zoneService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneDto> create(@Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(zoneService.create(request.name(), request.floor(), request.building()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneDto update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return zoneService.update(id, request.name(), request.floor(), request.building());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record ZoneRequest(@NotBlank String name, @NotBlank String floor, @NotBlank String building) {}
}
