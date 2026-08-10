package com.securitysuite.backend.surveillance;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.surveillance.dto.CreateMotionEventRequest;
import com.securitysuite.backend.surveillance.dto.FeedStatusDto;
import com.securitysuite.backend.surveillance.dto.MotionEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurveillanceService {

    private final MotionEventRepository motionEventRepository;
    private final DeviceRepository deviceRepository;
    private final com.securitysuite.backend.videoclip.VideoClipService videoClipService;

    @Autowired(required = false)
    private com.securitysuite.backend.alert.AlertService alertService;

    /**
     * Lists motion events with optional filtering by cameraId and/or detectedAt range.
     * All combinations are supported:
     *  - cameraId only       → findByCameraId
     *  - from/to only        → findByDetectedAtBetween
     *  - cameraId + from/to  → findByCameraIdAndDetectedAtBetween
     *  - none                → findAll (paginated)
     */
    public Page<MotionEventDto> listMotionEvents(String cameraId, Instant from, Instant to, Pageable pageable) {
        boolean hasCameraId = cameraId != null && !cameraId.isBlank();
        boolean hasRange    = from != null && to != null;

        Page<MotionEvent> page;
        if (hasCameraId && hasRange) {
            page = motionEventRepository.findByCameraIdAndDetectedAtBetween(cameraId, from, to, pageable);
        } else if (hasCameraId) {
            page = motionEventRepository.findByCameraId(cameraId, pageable);
        } else if (hasRange) {
            page = motionEventRepository.findByDetectedAtBetween(from, to, pageable);
        } else {
            page = motionEventRepository.findAll(pageable);
        }

        return page.map(this::toDto);
    }

    /**
     * Creates a motion event, optionally resolving the camera name from the device registry.
     * If the device is not found (e.g. unregistered gateway), the cameraId is used as a fallback name.
     * Optionally triggers video recording if camera has RTSP stream configured.
     */
    @Transactional
    public MotionEventDto createMotionEvent(CreateMotionEventRequest request) {
        String cameraName = resolveCameraName(request.cameraId());

        MotionEvent event = MotionEvent.builder()
                .cameraId(request.cameraId())
                .cameraName(cameraName)
                .detectedAt(Instant.now())
                .confidence(request.confidence())
                .build();

        event = motionEventRepository.save(event);
        log.info("Motion event created: camera={}, confidence={}, eventId={}",
                 request.cameraId(), request.confidence(), event.getId());

        triggerVideoRecording(request.cameraId(), event.getId());
        createAlertForHighConfidenceMotion(request, event);

        return toDto(event);
    }

    private void triggerVideoRecording(String cameraId, Long motionEventId) {
        findDeviceById(cameraId).ifPresent(device -> {
            if (device.getStreamUrl() != null && !device.getStreamUrl().isBlank()) {
                log.info("Triggering video recording for device: {}", device.getId());
                videoClipService.recordClip(device.getId(), 30, "MOTION", null);
            } else {
                log.debug("Device {} has no stream URL configured, skipping video recording", device.getId());
            }
        });
    }

    private void createAlertForHighConfidenceMotion(CreateMotionEventRequest request, MotionEvent event) {
        if (alertService == null) {
            log.debug("AlertService not available, skipping alert creation");
            return;
        }

        // Only create alerts for high-confidence motion events (>0.7)
        if (request.confidence() > 0.7) {
            findDeviceById(request.cameraId()).ifPresent(device -> {
                if (device.getZone() != null) {
                    com.securitysuite.backend.alert.AlertSeverity severity =
                        request.confidence() > 0.9
                            ? com.securitysuite.backend.alert.AlertSeverity.HIGH
                            : com.securitysuite.backend.alert.AlertSeverity.MEDIUM;

                    try {
                        alertService.create(new com.securitysuite.backend.alert.AlertService.CreateAlertRequest(
                            device.getZone().getId(),
                            device.getId(),
                            severity,
                            String.format("Motion detected by %s (confidence: %.1f%%)",
                                device.getName(), request.confidence() * 100)
                        ));
                        log.info("Alert created for motion event: camera={}, confidence={}",
                            request.cameraId(), request.confidence());
                    } catch (Exception e) {
                        log.error("Failed to create alert for motion event", e);
                    }
                } else {
                    log.debug("Device {} has no zone assigned, skipping alert creation", device.getId());
                }
            });
        }
    }

    /**
     * Returns the live feed status for a given camera (device).
     *
     * @throws NotFoundException if no device with the given ID exists
     */
    public FeedStatusDto getFeedStatus(String cameraId) {
        Device device = findDeviceById(cameraId)
                .orElseThrow(() -> new NotFoundException("Camera not found: " + cameraId));

        // TODO: Add lastHeartbeatAt field to Device entity and replace null below.
        Instant lastHeartbeatAt = null;

        return new FeedStatusDto(
                cameraId,
                device.getStatus().toString(),
                lastHeartbeatAt,
                "1080p"
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String resolveCameraName(String cameraId) {
        return findDeviceById(cameraId)
                .map(Device::getName)
                .orElse(cameraId);  // fall back to raw ID if device not registered
    }

    /**
     * Attempts to parse cameraId as a UUID and look up the corresponding Device.
     * Returns an empty Optional if the ID is malformed or not found.
     */
    private java.util.Optional<Device> findDeviceById(String cameraId) {
        try {
            UUID uuid = UUID.fromString(cameraId);
            return deviceRepository.findById(uuid);
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private MotionEventDto toDto(MotionEvent event) {
        return new MotionEventDto(
                event.getId(),
                event.getCameraId(),
                event.getCameraName(),
                event.getDetectedAt(),
                event.getConfidence()
        );
    }
}
