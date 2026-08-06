package com.securitysuite.backend.emergency;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/emergency")
@RequiredArgsConstructor
@Tag(name = "Emergency Response", description = "Emergency lockdown, evacuation, and crisis management")
public class EmergencyController {
    private final EmergencyService emergencyService;

    @GetMapping("/events")
    @Operation(summary = "List all emergency events")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyEventDto>> listEvents() {
        return ResponseEntity.ok(emergencyService.listAll());
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Get emergency event details")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(emergencyService.getById(id));
    }

    @GetMapping("/events/active")
    @Operation(summary = "Get active emergencies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EmergencyEventDto>> getActiveEvents() {
        return ResponseEntity.ok(emergencyService.listActive());
    }

    @GetMapping("/events/active/count")
    @Operation(summary = "Count active emergencies")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ActiveEmergencyCountResponse> getActiveCount() {
        return ResponseEntity.ok(new ActiveEmergencyCountResponse(emergencyService.countActive()));
    }

    @PostMapping("/events")
    @Operation(summary = "Trigger emergency event (CRITICAL)")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> triggerEmergency(
            @Valid @RequestBody TriggerEmergencyRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emergencyService.triggerEmergency(
                        request.eventType(),
                        request.severity(),
                        request.description(),
                        request.affectedZones(),
                        principal.getUsername()
                ));
    }

    @PatchMapping("/events/{id}/resolve")
    @Operation(summary = "Resolve emergency event")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> resolveEmergency(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveEmergencyRequest request) {
        return ResponseEntity.ok(emergencyService.resolve(id, request.responseActions()));
    }

    @PatchMapping("/events/{id}/all-clear")
    @Operation(summary = "Declare all-clear for emergency")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> declareAllClear(@PathVariable UUID id) {
        return ResponseEntity.ok(emergencyService.declareAllClear(id));
    }

    // ===== CONTACTS =====
    @GetMapping("/contacts")
    @Operation(summary = "List emergency contacts")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyContactDto>> listContacts() {
        return ResponseEntity.ok(emergencyService.listContacts());
    }

    @GetMapping("/contacts/enabled")
    @Operation(summary = "List enabled emergency contacts (by priority)")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyContactDto>> listEnabledContacts() {
        return ResponseEntity.ok(emergencyService.listEnabledContacts());
    }

    @PostMapping("/contacts")
    @Operation(summary = "Add emergency contact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmergencyContactDto> addContact(@Valid @RequestBody AddContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(emergencyService.addContact(
                        request.name(),
                        request.role(),
                        request.phoneNumber(),
                        request.email(),
                        request.priority()
                ));
    }

    @DeleteMapping("/contacts/{id}")
    @Operation(summary = "Delete emergency contact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID id) {
        emergencyService.deleteContact(id);
        return ResponseEntity.noContent().build();
    }

    // ===== DTOs =====
    public record TriggerEmergencyRequest(
            @NotNull EmergencyEventType eventType,
            @NotNull EmergencySeverity severity,
            @NotBlank String description,
            String affectedZones
    ) {}

    public record ResolveEmergencyRequest(@NotBlank String responseActions) {}

    public record AddContactRequest(
            @NotBlank String name,
            String role,
            @NotBlank String phoneNumber,
            String email,
            Integer priority
    ) {}

    public record ActiveEmergencyCountResponse(long count) {}
}
