package com.securitysuite.backend.surveillance;

import com.securitysuite.backend.surveillance.dto.CreateMotionEventRequest;
import com.securitysuite.backend.surveillance.dto.FeedStatusDto;
import com.securitysuite.backend.surveillance.dto.MotionEventDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/surveillance")
@RequiredArgsConstructor
@Tag(name = "Surveillance", description = "Motion events and camera feed status")
public class SurveillanceController {

    private final SurveillanceService surveillanceService;

    /**
     * Lists motion events with optional filtering by camera and/or date range.
     * Accessible by all authenticated users (ADMIN, SECURITY_OFFICER, VIEWER).
     *
     * @param cameraId optional camera device ID to filter by
     * @param from     optional inclusive start date (ISO-8601 date, e.g. "2024-01-15")
     * @param to       optional inclusive end date (ISO-8601 date, e.g. "2024-01-15")
     * @param page     zero-based page index (default 0)
     * @param size     page size (default 20)
     * @param sort     sort field and direction, e.g. "detectedAt,desc"
     */
    @GetMapping("/motion-events")
    @Operation(summary = "List motion events",
               description = "Returns a paginated list of motion detection events from surveillance cameras. Filter by camera ID and/or date range (ISO-8601 dates). Sorted by detection time (newest first) by default. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public Page<MotionEventDto> listMotionEvents(
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt,desc") String sort) {

        Instant fromInstant = parseDate(from);
        Instant toInstant   = to != null
                ? LocalDate.parse(to).atStartOfDay(ZoneOffset.UTC).plusDays(1).toInstant()  // end of the "to" day
                : null;

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return surveillanceService.listMotionEvents(cameraId, fromInstant, toInstant, pageable);
    }

    /**
     * Records a new motion event detected by a camera.
     * Restricted to ADMIN and SECURITY_OFFICER roles (device/gateway callers).
     */
    @PostMapping("/motion-events")
    @Operation(summary = "Record a motion event",
               description = "Creates a new motion detection event for a camera. Records camera ID, detection time, and optional snapshot URL. Typically called by surveillance systems or gateways. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<MotionEventDto> createMotionEvent(@Valid @RequestBody CreateMotionEventRequest request) {
        MotionEventDto created = surveillanceService.createMotionEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/feed-status/{cameraId}")
    @Operation(summary = "Get camera feed status",
               description = "Returns the current operational status of a camera feed including online/offline status, last heartbeat timestamp, and stream resolution. Used for monitoring camera health. Available to all authenticated users.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FeedStatusDto> getFeedStatus(@PathVariable String cameraId) {
        return ResponseEntity.ok(surveillanceService.getFeedStatus(cameraId));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses an optional ISO-8601 date string (yyyy-MM-dd) into an Instant at the
     * start of day in UTC. Returns {@code null} when the input is null or blank.
     */
    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
