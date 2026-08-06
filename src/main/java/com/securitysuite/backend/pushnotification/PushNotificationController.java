package com.securitysuite.backend.pushnotification;

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
@RequestMapping("/push-notifications")
@RequiredArgsConstructor
@Tag(name = "Push Notifications", description = "Expo push notification management (mobile app notifications)")
public class PushNotificationController {
    private final PushNotificationService pushNotificationService;

    @PostMapping("/devices/register")
    @Operation(summary = "Register device for push notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PushNotificationDeviceDto> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pushNotificationService.registerDevice(
                        principal.getUsername(),
                        request.token(),
                        request.deviceType(),
                        request.deviceName()
                ));
    }

    @DeleteMapping("/devices")
    @Operation(summary = "Delete/unregister device from push notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteDevice(
            @Valid @RequestBody DeleteDeviceRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        pushNotificationService.deleteDevice(request.token(), principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/devices/my")
    @Operation(summary = "Get my registered devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PushNotificationDeviceDto>> getMyDevices(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(pushNotificationService.getUserDevices(principal.getUsername()));
    }

    @PostMapping("/send/user/{userId}")
    @Operation(summary = "Send push notification to specific user")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<SendNotificationResponse> sendToUser(
            @PathVariable UUID userId,
            @Valid @RequestBody SendNotificationRequest request) {
        PushNotificationService.SendNotificationResult result = pushNotificationService.sendToUser(
                userId, request.title(), request.body(), request.data()
        );
        return ResponseEntity.ok(new SendNotificationResponse(
                result.successCount(),
                result.failureCount(),
                result.errors()
        ));
    }

    @PostMapping("/send/security-personnel")
    @Operation(summary = "Send push notification to all security personnel (ADMIN + SECURITY_OFFICER)")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<SendNotificationResponse> sendToSecurityPersonnel(
            @Valid @RequestBody SendNotificationRequest request) {
        PushNotificationService.SendNotificationResult result = pushNotificationService.sendToSecurityPersonnel(
                request.title(), request.body(), request.data()
        );
        return ResponseEntity.ok(new SendNotificationResponse(
                result.successCount(),
                result.failureCount(),
                result.errors()
        ));
    }

    @PostMapping("/send/users")
    @Operation(summary = "Send push notification to multiple users")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<SendNotificationResponse> sendToUsers(
            @Valid @RequestBody SendToUsersRequest request) {
        PushNotificationService.SendNotificationResult result = pushNotificationService.sendToUsers(
                request.userIds(), request.title(), request.body(), request.data()
        );
        return ResponseEntity.ok(new SendNotificationResponse(
                result.successCount(),
                result.failureCount(),
                result.errors()
        ));
    }

    @PostMapping("/send/alert")
    @Operation(summary = "Send alert notification to all security personnel")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<SendNotificationResponse> sendAlert(
            @Valid @RequestBody SendAlertRequest request) {
        PushNotificationService.SendNotificationResult result = pushNotificationService.sendAlertToSecurityPersonnel(
                request.alertType(), request.message()
        );
        return ResponseEntity.ok(new SendNotificationResponse(
                result.successCount(),
                result.failureCount(),
                result.errors()
        ));
    }

    @PostMapping("/send/emergency")
    @Operation(summary = "Send emergency notification to all security personnel")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<SendNotificationResponse> sendEmergency(
            @Valid @RequestBody SendEmergencyRequest request) {
        PushNotificationService.SendNotificationResult result = pushNotificationService.sendEmergencyNotification(
                request.emergencyType(), request.description()
        );
        return ResponseEntity.ok(new SendNotificationResponse(
                result.successCount(),
                result.failureCount(),
                result.errors()
        ));
    }

    // ===== Request/Response DTOs =====
    public record RegisterDeviceRequest(
            @NotBlank String token,
            @NotNull DeviceType deviceType,
            String deviceName
    ) {}

    public record DeleteDeviceRequest(
            @NotBlank String token
    ) {}

    public record SendNotificationRequest(
            @NotBlank String title,
            @NotBlank String body,
            Object data
    ) {}

    public record SendToUsersRequest(
            @NotNull List<UUID> userIds,
            @NotBlank String title,
            @NotBlank String body,
            Object data
    ) {}

    public record SendAlertRequest(
            @NotBlank String alertType,
            @NotBlank String message
    ) {}

    public record SendEmergencyRequest(
            @NotBlank String emergencyType,
            @NotBlank String description
    ) {}

    public record SendNotificationResponse(
            int successCount,
            int failureCount,
            List<String> errors
    ) {}
}
