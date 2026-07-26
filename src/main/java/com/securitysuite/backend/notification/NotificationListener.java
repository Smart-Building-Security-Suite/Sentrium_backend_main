package com.securitysuite.backend.notification;

import com.securitysuite.backend.alert.Alert;
import com.securitysuite.backend.alert.AlertSeverity;
import com.securitysuite.backend.user.Role;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@securitysuite.local}")
    private String fromAddress;

    @Async
    @EventListener
    @Transactional
    public void onAlertCreated(AlertCreatedEvent event) {
        Alert alert = event.alert();
        // Fetch only SECURITY_OFFICER users from the DB — avoids loading all users into memory
        List<User> officers = userRepository.findByRole(Role.SECURITY_OFFICER);
        log.info("Dispatching notifications for alert {} to {} security officer(s)", alert.getId(), officers.size());
        for (User user : officers) {
            saveNotification(user, alert, NotificationChannel.IN_APP);
            if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                saveNotification(user, alert, NotificationChannel.EMAIL);
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(fromAddress);
                    message.setTo(user.getEmail());
                    message.setSubject("[CRITICAL] Security alert: " + alert.getMessage());
                    message.setText("Critical security alert in zone " + alert.getZone().getName() + ". Message: " + alert.getMessage());
                    mailSender.send(message);
                } catch (Exception ex) {
                    log.warn("Email send failed for user {} on alert {}: {}", user.getEmail(), alert.getId(), ex.getMessage());
                }
            }
        }
    }

    private void saveNotification(User user, Alert alert, NotificationChannel channel) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setAlert(alert);
        notification.setChannel(channel);
        notificationRepository.save(notification);
    }
}
