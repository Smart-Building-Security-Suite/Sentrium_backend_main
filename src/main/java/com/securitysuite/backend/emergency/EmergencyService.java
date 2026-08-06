package com.securitysuite.backend.emergency;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.pushnotification.PushNotificationService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.websocket.WebSocketAlertPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyService {
    private final EmergencyEventRepository eventRepository;
    private final EmergencyContactRepository contactRepository;
    private final UserRepository userRepository;
    private final WebSocketAlertPublisher webSocketPublisher;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    @Transactional
    public EmergencyEventDto triggerEmergency(EmergencyEventType eventType, EmergencySeverity severity,
                                              String description, String affectedZones, String triggeredByPhoneNumber) {
        User triggeredBy = userRepository.findByPhoneNumber(triggeredByPhoneNumber)
                .orElseThrow(() -> new NotFoundException("User not found"));

        EmergencyEvent event = new EmergencyEvent();
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setDescription(description);
        event.setAffectedZones(affectedZones);
        event.setTriggeredBy(triggeredBy);
        event = eventRepository.save(event);

        log.warn("EMERGENCY TRIGGERED: type={}, severity={}, triggeredBy={}", eventType, severity, triggeredBy.getName());

        // 🆘 PUSH NOTIFICATION: Emergency Event
        if (pushNotificationService != null) {
            pushNotificationService.sendEmergencyNotification(
                eventType.name(),
                description
            );
        }

        // TODO: Additional emergency actions:
        // 1. Lock/unlock doors based on type (lockdown vs evacuation)
        // 2. Send SMS to emergency contacts
        // 3. Send Email to key personnel
        // 4. Activate emergency protocols

        return EmergencyEventDto.from(event);
    }

    @Transactional
    public EmergencyEventDto resolve(UUID eventId, String responseActions) {
        EmergencyEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Emergency event not found"));

        if (event.getStatus() != EmergencyStatus.ACTIVE) {
            throw new IllegalStateException("Emergency is not active");
        }

        event.setStatus(EmergencyStatus.RESOLVED);
        event.setResolvedAt(Instant.now());
        event.setResponseActions(responseActions);

        event = eventRepository.save(event);
        log.info("Emergency resolved: id={}", eventId);

        return EmergencyEventDto.from(event);
    }

    @Transactional
    public EmergencyEventDto declareAllClear(UUID eventId) {
        EmergencyEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Emergency event not found"));

        event.setAllClearAt(Instant.now());
        if (event.getStatus() == EmergencyStatus.ACTIVE) {
            event.setStatus(EmergencyStatus.RESOLVED);
            event.setResolvedAt(Instant.now());
        }

        event = eventRepository.save(event);
        log.info("Emergency all-clear declared: id={}", eventId);

        return EmergencyEventDto.from(event);
    }

    public List<EmergencyEventDto> listAll() {
        return eventRepository.findAll().stream()
                .map(EmergencyEventDto::from)
                .toList();
    }

    public List<EmergencyEventDto> listActive() {
        return eventRepository.findActiveEmergencies().stream()
                .map(EmergencyEventDto::from)
                .toList();
    }

    public long countActive() {
        return eventRepository.countActiveEmergencies();
    }

    public EmergencyEventDto getById(UUID id) {
        return EmergencyEventDto.from(eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Emergency event not found")));
    }

    // ===== CONTACTS =====
    public List<EmergencyContactDto> listContacts() {
        return contactRepository.findAll().stream()
                .map(EmergencyContactDto::from)
                .toList();
    }

    public List<EmergencyContactDto> listEnabledContacts() {
        return contactRepository.findByEnabledTrueOrderByPriorityAsc().stream()
                .map(EmergencyContactDto::from)
                .toList();
    }

    @Transactional
    public EmergencyContactDto addContact(String name, String role, String phoneNumber, String email, Integer priority) {
        EmergencyContact contact = new EmergencyContact();
        contact.setName(name);
        contact.setRole(role);
        contact.setPhoneNumber(phoneNumber);
        contact.setEmail(email);
        contact.setPriority(priority != null ? priority : 1);
        contact = contactRepository.save(contact);

        log.info("Emergency contact added: {}", name);
        return EmergencyContactDto.from(contact);
    }

    @Transactional
    public void deleteContact(UUID id) {
        contactRepository.deleteById(id);
        log.info("Emergency contact deleted: id={}", id);
    }
}
