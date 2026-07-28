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
    public List<NotificationSummary> list(@AuthenticationPrincipal UserDetails principal) {
        return notificationRepository.findByUserEmailOrderByUnreadFirst(principal.getUsername()).stream()
                .map(NotificationSummary::from)
                .toList();
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    @Transactional
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal UserDetails principal) {
        Notification notification = ownedNotification(id, principal.getUsername());
        notification.setReadAt(Instant.now());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByEmail(principal.getUsername());
        notificationRepository.markAllReadByUserId(user.getId(), java.time.Instant.now());
        return ResponseEntity.noContent().build();
    }

    private Notification ownedNotification(UUID id, String email) {
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!notification.getUser().getEmail().equals(email)) throw new NotFoundException("Notification not found");
        return notification;
    }
}
