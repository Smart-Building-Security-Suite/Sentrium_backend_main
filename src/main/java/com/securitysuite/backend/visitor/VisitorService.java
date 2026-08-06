package com.securitysuite.backend.visitor;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.pushnotification.PushNotificationService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisitorService {
    private final VisitorRepository visitorRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public Page<VisitorDto> listAll(VisitorStatus status, UUID hostId, Pageable pageable) {
        if (status != null) {
            return visitorRepository.findByStatus(status, pageable).map(VisitorDto::from);
        }
        if (hostId != null) {
            return visitorRepository.findByHostId(hostId, pageable).map(VisitorDto::from);
        }
        return visitorRepository.findAll(pageable).map(VisitorDto::from);
    }

    @Transactional
    public VisitorDto preRegister(CreateVisitorRequest request, String createdByPhoneNumber) {
        User host = userRepository.findById(request.hostId())
                .orElseThrow(() -> new NotFoundException("Host user not found"));

        User createdBy = userRepository.findByPhoneNumber(createdByPhoneNumber)
                .orElseThrow(() -> new NotFoundException("Creator user not found"));

        Visitor visitor = new Visitor();
        visitor.setName(request.name());
        visitor.setEmail(request.email());
        visitor.setPhoneNumber(request.phoneNumber());
        visitor.setCompany(request.company());
        visitor.setPurpose(request.purpose());
        visitor.setHost(host);
        visitor.setExpectedArrivalAt(request.expectedArrivalAt());
        visitor.setExpectedDepartureAt(request.expectedDepartureAt());
        visitor.setVehiclePlateNumber(request.vehiclePlateNumber());
        visitor.setStatus(VisitorStatus.PRE_REGISTERED);
        visitor.setCreatedBy(createdBy);
        visitor.setNotes(request.notes());

        visitor = visitorRepository.save(visitor);
        log.info("Visitor pre-registered: {} for host {}", visitor.getName(), host.getName());

        // TODO: Send notification to host
        return VisitorDto.from(visitor);
    }

    @Transactional
    public VisitorDto checkIn(UUID visitorId, String badgeNumber) {
        Visitor visitor = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new NotFoundException("Visitor not found"));

        if (visitor.getStatus() != VisitorStatus.PRE_REGISTERED) {
            throw new IllegalStateException("Visitor cannot be checked in from status: " + visitor.getStatus());
        }

        visitor.setStatus(VisitorStatus.CHECKED_IN);
        visitor.setCheckedInAt(Instant.now());
        visitor.setBadgeNumber(badgeNumber);

        visitor = visitorRepository.save(visitor);
        log.info("Visitor checked in: {} with badge {}", visitor.getName(), badgeNumber);

        // 🔔 PUSH NOTIFICATION: Notify Host that Visitor Arrived
        if (pushNotificationService != null && visitor.getHost() != null) {
            pushNotificationService.sendToUser(
                visitor.getHost().getId(),
                "👤 Your Visitor Has Arrived",
                visitor.getName() + " from " + (visitor.getCompany() != null ? visitor.getCompany() : "N/A") + " has checked in",
                Map.of(
                    "visitorId", visitor.getId().toString(),
                    "visitorName", visitor.getName(),
                    "badgeNumber", badgeNumber,
                    "type", "VISITOR_CHECKED_IN"
                )
            );
        }

        return VisitorDto.from(visitor);
    }

    @Transactional
    public VisitorDto checkOut(UUID visitorId) {
        Visitor visitor = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new NotFoundException("Visitor not found"));

        if (visitor.getStatus() != VisitorStatus.CHECKED_IN) {
            throw new IllegalStateException("Visitor is not checked in");
        }

        visitor.setStatus(VisitorStatus.CHECKED_OUT);
        visitor.setCheckedOutAt(Instant.now());

        visitor = visitorRepository.save(visitor);
        log.info("Visitor checked out: {}", visitor.getName());

        return VisitorDto.from(visitor);
    }

    public List<VisitorDto> getCurrentVisitors() {
        return visitorRepository.findCurrentlyOnPremises()
                .stream()
                .map(VisitorDto::from)
                .toList();
    }

    public long getCurrentVisitorCount() {
        return visitorRepository.countCurrentVisitors();
    }

    public VisitorDto getById(UUID id) {
        return VisitorDto.from(visitorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Visitor not found")));
    }

    @Transactional
    public VisitorDto cancel(UUID visitorId) {
        Visitor visitor = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new NotFoundException("Visitor not found"));

        visitor.setStatus(VisitorStatus.CANCELLED);
        return VisitorDto.from(visitorRepository.save(visitor));
    }

    public record CreateVisitorRequest(
            String name,
            String email,
            String phoneNumber,
            String company,
            String purpose,
            UUID hostId,
            Instant expectedArrivalAt,
            Instant expectedDepartureAt,
            String vehiclePlateNumber,
            String notes
    ) {}
}
