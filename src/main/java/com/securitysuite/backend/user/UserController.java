package com.securitysuite.backend.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users in the system",
               description = "Retrieves a list of all registered users with their details including role and status. Admin access only.")
    public List<UserDto> list() {
        return userService.listAll();
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user's role",
               description = "Changes the role of a specific user (ADMIN, SECURITY_OFFICER, or VIEWER). Admin access only.")
    public UserDto updateRole(@PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request) {
        return userService.updateRole(id, request.role());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a user without deleting history",
               description = "Soft-deletes a user account by marking it inactive. Preserves all audit history and associated records. Admin access only.")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    public record RoleUpdateRequest(@NotNull Role role) {}
}
