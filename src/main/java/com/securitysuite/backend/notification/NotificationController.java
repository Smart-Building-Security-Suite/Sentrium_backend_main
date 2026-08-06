package com.securitysuite.backend.notification;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List my notifications",
               description = "Retrieves all notifications for the authenticated user sorted with unread notifications first. Includes alerts, incidents, emergencies, and system messages.")
    public List<NotificationSummary> list(@AuthenticationPrincipal UserDetails principal) {
        return notificationRepository.findByUserPhoneNumberOrderByUnreadFirst(principal.getUsername()).stream()
                .map(NotificationSummary::from)
                .toList();
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read",
               description = "Marks a specific notification as read by recording the read timestamp. The notification must belong to the authenticated user.")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal UserDetails principal) {
        Notification notification = ownedNotification(id, principal.getUsername());
        notification.setReadAt(Instant.now());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read",
               description = "Bulk marks all notifications for the authenticated user as read. Used for clearing notification queues.")
    @Transactional
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByPhoneNumber(principal.getUsername());
        notificationRepository.markAllReadByUserId(user.getId(), java.time.Instant.now());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications for badge display",
               description = "Returns the number of unread notifications for the authenticated user. Used to display notification badge counts in the UI.")
    public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal UserDetails principal) {
        long count = notificationRepository.countUnreadByPhoneNumber(principal.getUsername());
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }

    private Notification ownedNotification(UUID id, String phoneNumber) {
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!notification.getUser().getPhoneNumber().equals(phoneNumber)) throw new NotFoundException("Notification not found");
        return notification;
    }

    public record UnreadCountResponse(long unreadCount) {}
}
