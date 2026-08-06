package com.securitysuite.backend.pushnotification;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import io.github.hlspablo.exposdkjava.ExpoPushNotificationClient;
import io.github.hlspablo.exposdkjava.dto.PushNotification;
import io.github.hlspablo.exposdkjava.dto.response.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {
    private final PushNotificationDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final ExpoPushNotificationClient expoPushClient;

    /**
     * Register a device for push notifications
     */
    @Transactional
    public PushNotificationDeviceDto registerDevice(String userPhoneNumber, String expoToken,
                                                     DeviceType deviceType, String deviceName) {
        User user = userRepository.findByPhoneNumber(userPhoneNumber)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if token already exists
        deviceRepository.findByExpoToken(expoToken).ifPresent(existingDevice -> {
            // Reactivate if inactive, or update user if different
            if (!existingDevice.getActive() || !existingDevice.getUser().getId().equals(user.getId())) {
                existingDevice.setUser(user);
                existingDevice.setActive(true);
                existingDevice.setDeviceType(deviceType);
                existingDevice.setDeviceName(deviceName);
                existingDevice.setLastUsedAt(Instant.now());
                deviceRepository.save(existingDevice);
            }
        });

        // Create new device registration if not exists
        PushNotificationDevice device = deviceRepository.findByExpoToken(expoToken)
                .orElseGet(() -> {
                    PushNotificationDevice newDevice = new PushNotificationDevice();
                    newDevice.setUser(user);
                    newDevice.setExpoToken(expoToken);
                    newDevice.setDeviceType(deviceType);
                    newDevice.setDeviceName(deviceName);
                    return deviceRepository.save(newDevice);
                });

        log.info("Push notification device registered: user={}, type={}, token={}",
                user.getName(), deviceType, expoToken.substring(0, 20) + "...");

        return PushNotificationDeviceDto.from(device);
    }

    /**
     * Delete/deactivate a device
     */
    @Transactional
    public void deleteDevice(String expoToken, String userPhoneNumber) {
        User user = userRepository.findByPhoneNumber(userPhoneNumber)
                .orElseThrow(() -> new NotFoundException("User not found"));

        PushNotificationDevice device = deviceRepository.findByExpoToken(expoToken)
                .orElseThrow(() -> new NotFoundException("Device not found"));

        // Verify ownership
        if (!device.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("Device does not belong to this user");
        }

        deviceRepository.delete(device);
        log.info("Push notification device deleted: user={}, token={}", user.getName(), expoToken.substring(0, 20) + "...");
    }

    /**
     * Get user's registered devices
     */
    public List<PushNotificationDeviceDto> getUserDevices(String userPhoneNumber) {
        User user = userRepository.findByPhoneNumber(userPhoneNumber)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return deviceRepository.findByUserId(user.getId()).stream()
                .map(PushNotificationDeviceDto::from)
                .toList();
    }

    /**
     * Send push notification to specific user
     */
    public SendNotificationResult sendToUser(UUID userId, String title, String body, Object data) {
        List<PushNotificationDevice> devices = deviceRepository.findActiveDevicesByUser(userId);

        if (devices.isEmpty()) {
            log.warn("No active devices found for user: {}", userId);
            return new SendNotificationResult(0, 0, List.of("No active devices"));
        }

        return sendPushNotifications(devices, title, body, data);
    }

    /**
     * Send push notification to all security personnel (ADMIN + SECURITY_OFFICER)
     */
    public SendNotificationResult sendToSecurityPersonnel(String title, String body, Object data) {
        List<PushNotificationDevice> devices = deviceRepository.findAllSecurityPersonnelDevices();

        if (devices.isEmpty()) {
            log.warn("No active devices found for security personnel");
            return new SendNotificationResult(0, 0, List.of("No active devices"));
        }

        return sendPushNotifications(devices, title, body, data);
    }

    /**
     * Send push notification to multiple users
     */
    public SendNotificationResult sendToUsers(List<UUID> userIds, String title, String body, Object data) {
        List<PushNotificationDevice> devices = userIds.stream()
                .flatMap(userId -> deviceRepository.findActiveDevicesByUser(userId).stream())
                .distinct()
                .collect(Collectors.toList());

        if (devices.isEmpty()) {
            log.warn("No active devices found for specified users");
            return new SendNotificationResult(0, 0, List.of("No active devices"));
        }

        return sendPushNotifications(devices, title, body, data);
    }

    /**
     * Core method to send push notifications via Expo
     */
    private SendNotificationResult sendPushNotifications(List<PushNotificationDevice> devices,
                                                         String title, String body, Object data) {
        List<String> expoTokens = devices.stream()
                .map(PushNotificationDevice::getExpoToken)
                .toList();

        PushNotification notification = new PushNotification();
        notification.setTo(expoTokens);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setSound("default");
        notification.setPriority("high");

        if (data != null) {
            notification.setData(data);
        }

        try {
            List<TicketResponse.Ticket> tickets = expoPushClient.sendPushNotifications(List.of(notification));

            int successCount = 0;
            int failureCount = 0;
            List<String> errors = new ArrayList<>();

            for (TicketResponse.Ticket ticket : tickets) {
                if ("ok".equalsIgnoreCase(ticket.getStatus())) {
                    successCount++;
                } else {
                    failureCount++;
                    if (ticket.getMessage() != null) {
                        errors.add(ticket.getMessage());
                    }
                }
            }

            // Update last used timestamp for successfully sent devices
            if (successCount > 0) {
                Instant now = Instant.now();
                devices.forEach(device -> {
                    device.setLastUsedAt(now);
                    deviceRepository.save(device);
                });
            }

            log.info("Push notifications sent: success={}, failure={}, total={}",
                    successCount, failureCount, tickets.size());

            return new SendNotificationResult(successCount, failureCount, errors);

        } catch (Exception e) {
            log.error("Failed to send push notifications: {}", e.getMessage(), e);
            return new SendNotificationResult(0, devices.size(), List.of(e.getMessage()));
        }
    }

    /**
     * Send alert notification to all security personnel
     */
    public SendNotificationResult sendAlertToSecurityPersonnel(String alertType, String message) {
        return sendToSecurityPersonnel(
                "🚨 Security Alert: " + alertType,
                message,
                new AlertData(alertType, Instant.now())
        );
    }

    /**
     * Send emergency notification to all security personnel
     */
    public SendNotificationResult sendEmergencyNotification(String emergencyType, String description) {
        return sendToSecurityPersonnel(
                "🆘 EMERGENCY: " + emergencyType,
                description,
                new EmergencyData(emergencyType, description, Instant.now())
        );
    }

    // ===== DTOs =====
    public record SendNotificationResult(int successCount, int failureCount, List<String> errors) {}

    public record AlertData(String alertType, Instant timestamp) {}

    public record EmergencyData(String emergencyType, String description, Instant timestamp) {}
}
