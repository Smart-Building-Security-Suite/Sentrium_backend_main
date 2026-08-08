package com.securitysuite.backend.mobileaccess;

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
@RequestMapping("/mobile-access")
@RequiredArgsConstructor
@Tag(name = "Mobile QR Access", description = "Generate and validate QR codes for mobile door access")
public class MobileAccessController {
    private final MobileAccessService mobileAccessService;

    @PostMapping("/tokens")
    @Operation(summary = "Generate QR access token",
               description = "Creates a temporary QR code token for mobile door access. Specify user, optional device/zone restrictions, duration, max uses, and purpose. Used for time-limited access grants. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<MobileAccessTokenDto> generateToken(@Valid @RequestBody GenerateTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mobileAccessService.generateToken(
                        request.userId(),
                        request.deviceId(),
                        request.zoneId(),
                        request.durationMinutes(),
                        request.maxUses(),
                        request.purpose()
                ));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate QR code at door (called by device/gateway)",
               description = "Validates a QR code presented at an access control device. Checks token validity, expiration, usage limits, and device/zone restrictions. Returns access grant/deny decision. Called by access control hardware. Admin and Security Officer access (production uses API keys).")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')") // In production, this would be called by device with API key
    public ResponseEntity<ValidationResponse> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        MobileAccessService.ValidationResult result = mobileAccessService.validateToken(
                request.qrCodeData(),
                request.deviceId()
        );

        return ResponseEntity.ok(new ValidationResponse(
                result.granted(),
                result.message(),
                result.token() != null ? result.token().userId() : null,
                result.token() != null ? result.token().userName() : null
        ));
    }

    @GetMapping("/tokens")
    @Operation(summary = "List my tokens",
               description = "Retrieves all QR access tokens for the authenticated user including active, expired, and revoked tokens. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MobileAccessTokenDto>> getMyTokens(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(mobileAccessService.getMyTokens(principal.getUsername()));
    }

    @GetMapping("/tokens/{id}")
    @Operation(summary = "Get token details",
               description = "Retrieves detailed information about a specific QR access token including usage count, expiration, and restrictions. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MobileAccessTokenDto> getToken(@PathVariable UUID id) {
        return ResponseEntity.ok(mobileAccessService.getToken(id));
    }

    @DeleteMapping("/tokens/{id}")
    @Operation(summary = "Revoke token",
               description = "Immediately invalidates a QR access token preventing further use. Used when access should be removed before expiration. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Void> revokeToken(@PathVariable UUID id) {
        mobileAccessService.revokeToken(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tokens/user/{userId}")
    @Operation(summary = "Get user's tokens",
               description = "Retrieves all QR access tokens issued to a specific user. Used for access management and auditing. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<MobileAccessTokenDto>> getUserTokens(@PathVariable UUID userId) {
        return ResponseEntity.ok(mobileAccessService.getUserTokens(userId));
    }

    @GetMapping("/tokens/user/{userId}/active")
    @Operation(summary = "Get user's active tokens",
               description = "Retrieves only currently valid (not expired, not revoked) QR access tokens for a user. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<List<MobileAccessTokenDto>> getUserActiveTokens(@PathVariable UUID userId) {
        return ResponseEntity.ok(mobileAccessService.getUserActiveTokens(userId));
    }

    // ===== DTOs =====
    public record GenerateTokenRequest(
            @NotNull UUID userId,
            UUID deviceId,
            UUID zoneId,
            Integer durationMinutes, // Defaults to 60
            Integer maxUses, // NULL = unlimited
            String purpose
    ) {}

    public record ValidateTokenRequest(
            @NotBlank String qrCodeData,
            @NotNull UUID deviceId
    ) {}

    public record ValidationResponse(
            boolean granted,
            String message,
            UUID userId,
            String userName
    ) {}
}
