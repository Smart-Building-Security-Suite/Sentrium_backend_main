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
    @Operation(summary = "List all emergency events",
               description = "Retrieves the complete history of emergency events including lockdowns, evacuations, fires, etc. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyEventDto>> listEvents() {
        return ResponseEntity.ok(emergencyService.listAll());
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Get emergency event details",
               description = "Retrieves complete details of a specific emergency event including timeline, affected zones, and response actions. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(emergencyService.getById(id));
    }

    @GetMapping("/events/active")
    @Operation(summary = "Get active emergencies",
               description = "Returns all currently active emergency events (not yet resolved or all-clear). Critical for real-time monitoring. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EmergencyEventDto>> getActiveEvents() {
        return ResponseEntity.ok(emergencyService.listActive());
    }

    @GetMapping("/events/active/count")
    @Operation(summary = "Count active emergencies",
               description = "Returns the number of ongoing emergency events. Used for dashboard alerts. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ActiveEmergencyCountResponse> getActiveCount() {
        return ResponseEntity.ok(new ActiveEmergencyCountResponse(emergencyService.countActive()));
    }

    @PostMapping("/events")
    @Operation(summary = "Trigger emergency event (CRITICAL)",
               description = "Initiates a facility-wide emergency (LOCKDOWN, EVACUATION, FIRE, MEDICAL, etc.). Triggers notifications to emergency contacts and can activate automated responses. Records initiator and timestamp. Admin and Security Officer access only.")
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
    @Operation(summary = "Resolve emergency event",
               description = "Marks an emergency as under control/resolved. Records response actions taken and resolution time. Does not lift restrictions until all-clear is declared. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> resolveEmergency(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveEmergencyRequest request) {
        return ResponseEntity.ok(emergencyService.resolve(id, request.responseActions()));
    }

    @PatchMapping("/events/{id}/all-clear")
    @Operation(summary = "Declare all-clear for emergency",
               description = "Declares the emergency fully over and lifts all emergency restrictions. Notifies all personnel that normal operations can resume. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<EmergencyEventDto> declareAllClear(@PathVariable UUID id) {
        return ResponseEntity.ok(emergencyService.declareAllClear(id));
    }

    // ===== CONTACTS =====
    @GetMapping("/contacts")
    @Operation(summary = "List emergency contacts",
               description = "Retrieves all emergency contacts (police, fire, medical, facility management) with phone numbers and emails. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyContactDto>> listContacts() {
        return ResponseEntity.ok(emergencyService.listContacts());
    }

    @GetMapping("/contacts/enabled")
    @Operation(summary = "List enabled emergency contacts (by priority)",
               description = "Retrieves only active emergency contacts sorted by priority. Used for automated notification sequences during emergencies. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<EmergencyContactDto>> listEnabledContacts() {
        return ResponseEntity.ok(emergencyService.listEnabledContacts());
    }

    @PostMapping("/contacts")
    @Operation(summary = "Add emergency contact",
               description = "Adds a new emergency contact with name, role, phone, email, and notification priority. Contacts are notified when emergencies are triggered. Admin only.")
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
    @Operation(summary = "Delete emergency contact",
               description = "Removes an emergency contact from the notification list. Admin only.")
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
