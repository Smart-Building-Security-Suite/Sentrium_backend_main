package com.securitysuite.backend.zone;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    @Operation(summary = "List all zones",
               description = "Retrieves all security zones (physical areas) in the facility. Supports pagination with sorting by name, floor, or building. Use paginated=true for large datasets.")
    public ResponseEntity<?> list(
            @RequestParam(required = false, defaultValue = "false") boolean paginated,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {

        if (paginated) {
            String[] sortParts = sort.split(",");
            Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));
            return ResponseEntity.ok(zoneService.listAllPaginated(pageable));
        }

        return ResponseEntity.ok(zoneService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new zone",
               description = "Defines a new security zone with name, floor, and building. Zones are used to organize devices and control access. Admin only.")
    public ResponseEntity<ZoneDto> create(@Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(zoneService.create(request.name(), request.floor(), request.building()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update zone details",
               description = "Modifies the name, floor, or building of an existing zone. Admin only.")
    public ZoneDto update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return zoneService.update(id, request.name(), request.floor(), request.building());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a zone",
               description = "Permanently removes a zone. Ensure no devices or access rules reference this zone before deletion. Admin only.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record ZoneRequest(@NotBlank String name, @NotBlank String floor, @NotBlank String building) {}
}
