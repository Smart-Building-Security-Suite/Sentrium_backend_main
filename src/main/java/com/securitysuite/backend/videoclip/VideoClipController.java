package com.securitysuite.backend.videoclip;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/video-clips")
@RequiredArgsConstructor
@Tag(name = "Video Clips", description = "Manage recorded video clips from surveillance cameras")
public class VideoClipController {

    private final VideoClipService videoClipService;

    @GetMapping
    @Operation(summary = "List video clips",
               description = "Retrieves paginated list of recorded video clips. Filter by camera, date range, or trigger type. Sorted by start time (newest first) by default. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<VideoClipService.VideoClipDto> listClips(
            @RequestParam(required = false) UUID cameraId,
            @RequestParam(required = false) String from, // ISO date
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String triggerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startTime,desc") String sort) {

        Instant fromInstant = parseDate(from);
        Instant toInstant = parseDate(to);

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        return videoClipService.listClips(cameraId, fromInstant, toInstant, triggerType, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get video clip details",
               description = "Retrieves metadata for a specific video clip including file size, duration, and retention date. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VideoClipService.VideoClipDto> getClip(@PathVariable UUID id) {
        return ResponseEntity.ok(videoClipService.getClip(id));
    }

    @PostMapping("/record")
    @Operation(summary = "Start recording a video clip",
               description = "Triggers a video recording from a camera's RTSP stream. Specify camera, duration, and trigger reason. Recording happens asynchronously. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<RecordingResponse> recordClip(@Valid @RequestBody RecordClipRequest request) {
        videoClipService.recordClip(
                request.cameraId(),
                request.durationSeconds() != null ? request.durationSeconds() : 30,
                request.triggerType() != null ? request.triggerType() : "MANUAL",
                request.triggerEventId()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                new RecordingResponse("Recording started", "Recording will complete in background")
        );
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download video clip file",
               description = "Streams the video file for download. Returns MP4 file. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<Resource> downloadClip(@PathVariable UUID id) {
        VideoClipService.VideoClipDto clip = videoClipService.getClip(id);

        // Get file path from clip metadata
        // In production, this would fetch from S3 or CDN
        File videoFile = new File(clip.fileUrl()); // Assuming fileUrl is local path for now

        if (!videoFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(videoFile);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + videoFile.getName() + "\"")
                .body(resource);
    }

    @GetMapping("/camera/{cameraId}")
    @Operation(summary = "Get clips for a specific camera",
               description = "Retrieves all video clips recorded by a specific camera. Paginated results sorted by newest first. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public Page<VideoClipService.VideoClipDto> getCameraClips(
            @PathVariable UUID cameraId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        return videoClipService.listClips(cameraId, null, null, null, pageable);
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive video clip",
               description = "Marks clip as archived without deleting the file. Archived clips don't count toward retention policies. Admin access.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archiveClip(@PathVariable UUID id) {
        videoClipService.archiveClip(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete video clip",
               description = "Permanently deletes the video clip file and database record. This action cannot be undone. Admin access.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClip(@PathVariable UUID id) {
        videoClipService.deleteClip(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Get storage statistics",
               description = "Returns total storage usage, clip count, and average clip size. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<VideoClipService.StorageStatsDto> getStats() {
        return ResponseEntity.ok(videoClipService.getStorageStats());
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Manually trigger retention cleanup",
               description = "Deletes expired clips based on retention policy. Normally runs automatically at 2 AM daily. Admin access.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerCleanup() {
        videoClipService.cleanupExpiredClips();
        return ResponseEntity.ok("Cleanup job started");
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record RecordClipRequest(
            @NotNull UUID cameraId,
            Integer durationSeconds, // Default 30
            String triggerType, // MOTION, ALERT, MANUAL, SCHEDULED
            UUID triggerEventId
    ) {}

    public record RecordingResponse(
            String status,
            String message
    ) {}

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
