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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlertCreated(AlertCreatedEvent event) {
        Alert alert = event.alert();
        List<User> recipients = new ArrayList<>();
        recipients.addAll(userRepository.findByRole(Role.SECURITY_OFFICER));
        recipients.addAll(userRepository.findByRole(Role.ADMIN));
        log.info("Dispatching notifications for alert {} to {} security personnel", alert.getId(), recipients.size());
        for (User user : recipients) {
            saveNotification(user, alert, NotificationChannel.IN_APP);
            if (alert.getSeverity() == AlertSeverity.CRITICAL && user.getEmail() != null && !user.getEmail().isBlank()) {
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
