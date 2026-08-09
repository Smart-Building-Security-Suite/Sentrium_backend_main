package com.securitysuite.backend.videoclip;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing video clip recording and playback.
 * Supports RTSP stream recording using FFmpeg.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoClipService {

    private final VideoClipRepository videoClipRepository;
    private final DeviceRepository deviceRepository;

    @Value("${app.video.storage-path:${user.home}/sentrium/videos}")
    private String videoStoragePath;

    @Value("${app.video.retention-days:30}")
    private int defaultRetentionDays;

    @Value("${app.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /**
     * List video clips with optional filtering
     */
    public Page<VideoClipDto> listClips(UUID cameraId, Instant from, Instant to,
                                        String triggerType, Pageable pageable) {
        Page<VideoClip> clips;

        if (cameraId != null && from != null && to != null) {
            clips = videoClipRepository.findByCameraIdAndStartTimeBetween(cameraId, from, to, pageable);
        } else if (cameraId != null) {
            clips = videoClipRepository.findByCameraIdOrderByStartTimeDesc(cameraId, pageable);
        } else if (triggerType != null) {
            clips = videoClipRepository.findByTriggerTypeOrderByStartTimeDesc(triggerType, pageable);
        } else {
            clips = videoClipRepository.findAll(pageable);
        }

        return clips.map(this::toDto);
    }

    /**
     * Get clip by ID
     */
    public VideoClipDto getClip(UUID id) {
        VideoClip clip = videoClipRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video clip not found"));
        return toDto(clip);
    }

    /**
     * Record a video clip from RTSP stream
     */
    @Async
    @Transactional
    public void recordClip(UUID cameraId, int durationSeconds, String triggerType, UUID triggerEventId) {
        log.info("Recording request received: camera={}, duration={}s, trigger={}", cameraId, durationSeconds, triggerType);

        Device camera = deviceRepository.findById(cameraId)
                .orElseThrow(() -> new NotFoundException("Camera not found: " + cameraId));

        if (camera.getStreamUrl() == null || camera.getStreamUrl().isBlank()) {
            log.warn("Camera {} ({}) has no stream URL configured, creating simulated video clip", camera.getName(), cameraId);
            recordSimulatedClip(camera, durationSeconds, triggerType, triggerEventId);
            return;
        }

        // SECURITY: Validate and sanitize stream URL to prevent command injection
        String streamUrl = camera.getStreamUrl();
        if (!isValidStreamUrl(streamUrl)) {
            log.error("Invalid stream URL format for camera {}: {}", cameraId, streamUrl);
            throw new IllegalArgumentException("Invalid stream URL format. Must be rtsp://, http://, or https://");
        }

        log.info("Camera found: name={}, streamUrl={}, resolution={}",
                 camera.getName(), streamUrl, camera.getStreamResolution());

        Instant startTime = Instant.now();
        String filename = generateFilename(cameraId, startTime);
        String outputPath = videoStoragePath + "/" + filename;

        // Ensure storage directory exists
        new File(videoStoragePath).mkdirs();

        log.info("Starting recording: camera={}, duration={}s, output={}", cameraId, durationSeconds, outputPath);

        try {
            // Build FFmpeg command
            // SECURITY: Use sanitized stream URL
            String[] command = new String[] {
                    ffmpegPath,
                    "-rtsp_transport", "tcp",
                    "-i", streamUrl,  // Already validated
                    "-t", String.valueOf(durationSeconds),
                    "-c", "copy", // Copy codec (no re-encoding)
                    "-movflags", "+faststart", // Optimize for web playback
                    "-y", // Overwrite output file
                    outputPath
            };

            log.info("FFmpeg command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            log.info("FFmpeg process started, waiting for recording to complete...");

            // Log FFmpeg output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            // Wait for completion with timeout
            boolean completed = process.waitFor(durationSeconds + 30, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("FFmpeg recording timeout for camera {} after {} seconds", cameraId, durationSeconds + 30);
                throw new RuntimeException("Recording timeout");
            }

            int exitCode = process.exitValue();
            log.info("FFmpeg process completed with exit code: {}", exitCode);

            if (exitCode != 0) {
                log.error("FFmpeg recording failed with exit code {} for camera {}", exitCode, cameraId);
                throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
            }

            // Get file size
            File videoFile = new File(outputPath);
            if (!videoFile.exists()) {
                log.error("Video file not created at path: {}", outputPath);
                throw new RuntimeException("Video file not created");
            }

            log.info("Video file created successfully: {} ({} bytes)", outputPath, videoFile.length());

            long fileSize = videoFile.length();
            Instant endTime = Instant.now();

            // Create video clip record
            VideoClip clip = new VideoClip();
            clip.setCamera(camera);
            clip.setStartTime(startTime);
            clip.setEndTime(endTime);
            clip.setDurationSeconds(durationSeconds);
            clip.setFilePath(outputPath);
            clip.setFileUrl(outputPath); // Set URL to local path for now (in production, use CDN/S3 URL)
            clip.setFileSizeBytes(fileSize);
            clip.setFormat("MP4");
            clip.setResolution(camera.getStreamResolution() != null ? camera.getStreamResolution() : "Unknown");
            clip.setTriggerType(triggerType);
            clip.setTriggerEventId(triggerEventId);
            clip.setRetentionUntil(Instant.now().plus(defaultRetentionDays, ChronoUnit.DAYS));

            videoClipRepository.save(clip);

            log.info("Video clip recorded successfully: id={}, size={}MB, path={}", clip.getId(), fileSize / 1024 / 1024, outputPath);

            // Generate thumbnail asynchronously
            generateThumbnail(clip.getId(), outputPath);

        } catch (Exception e) {
            log.error("Failed to record video clip for camera {}", cameraId, e);
        }
    }

    /**
     * Generate thumbnail for video clip
     */
    @Async
    protected void generateThumbnail(UUID clipId, String videoPath) {
        try {
            String thumbnailPath = videoPath.replace(".mp4", "_thumb.jpg");

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", videoPath,
                    "-ss", "00:00:01", // Take frame at 1 second
                    "-vframes", "1",
                    "-vf", "scale=320:240",
                    "-y",
                    thumbnailPath
            );

            Process process = processBuilder.start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);

            if (completed && process.exitValue() == 0) {
                VideoClip clip = videoClipRepository.findById(clipId).orElse(null);
                if (clip != null) {
                    clip.setThumbnailPath(thumbnailPath);
                    clip.setThumbnailUrl(thumbnailPath); // Set URL to local path for now (in production, use CDN/S3 URL)
                    videoClipRepository.save(clip);
                    log.info("Thumbnail generated for clip {}: {}", clipId, thumbnailPath);
                }
            } else {
                log.warn("Thumbnail generation failed for clip {}: completed={}, exitCode={}",
                         clipId, completed, completed ? process.exitValue() : "N/A");
            }
        } catch (Exception e) {
            log.error("Failed to generate thumbnail for clip {}", clipId, e);
        }
    }

    /**
     * Archive clip (soft delete)
     */
    @Transactional
    public void archiveClip(UUID id) {
        VideoClip clip = videoClipRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video clip not found"));

        clip.setArchived(true);
        clip.setArchivedAt(Instant.now());
        videoClipRepository.save(clip);

        log.info("Video clip archived: {}", id);
    }

    /**
     * Delete clip (hard delete - removes file)
     */
    @Transactional
    public void deleteClip(UUID id) {
        VideoClip clip = videoClipRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Video clip not found"));

        // Delete physical files
        if (clip.getFilePath() != null) {
            File videoFile = new File(clip.getFilePath());
            if (videoFile.exists()) {
                videoFile.delete();
            }
        }

        if (clip.getThumbnailPath() != null) {
            File thumbFile = new File(clip.getThumbnailPath());
            if (thumbFile.exists()) {
                thumbFile.delete();
            }
        }

        videoClipRepository.delete(clip);
        log.info("Video clip deleted: {}", id);
    }

    /**
     * Get storage statistics
     */
    public StorageStatsDto getStorageStats() {
        long totalBytes = videoClipRepository.getTotalStorageUsed();
        long clipCount = videoClipRepository.count();

        return new StorageStatsDto(
                totalBytes,
                totalBytes / 1024.0 / 1024.0 / 1024.0, // GB
                clipCount,
                clipCount > 0 ? totalBytes / clipCount : 0
        );
    }

    /**
     * Cleanup expired clips (runs nightly)
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    @Transactional
    public void cleanupExpiredClips() {
        List<VideoClip> expiredClips = videoClipRepository.findExpiredClips(Instant.now());

        int deleted = 0;
        for (VideoClip clip : expiredClips) {
            try {
                deleteClip(clip.getId());
                deleted++;
            } catch (Exception e) {
                log.error("Failed to delete expired clip {}", clip.getId(), e);
            }
        }

        log.info("Cleanup completed: {} expired clips deleted", deleted);
    }

    /**
     * Record a simulated video clip for testing/demonstration purposes.
     * Creates a database record without actually recording video from the stream.
     */
    @Transactional
    private void recordSimulatedClip(Device camera, int durationSeconds, String triggerType, UUID triggerEventId) {
        try {
            Instant startTime = Instant.now();
            Instant endTime = startTime.plusSeconds(durationSeconds);

            VideoClip clip = new VideoClip();
            clip.setCamera(camera);
            clip.setStartTime(startTime);
            clip.setEndTime(endTime);
            clip.setDurationSeconds(durationSeconds);

            String filename = generateFilename(camera.getId(), startTime);
            String simulatedPath = videoStoragePath + "/simulated/" + filename;

            clip.setFilePath(simulatedPath);
            clip.setFileUrl(simulatedPath);
            clip.setFileSizeBytes(5242880L); // Simulate 5MB file
            clip.setFormat("MP4");
            clip.setResolution(camera.getStreamResolution() != null ? camera.getStreamResolution() : "1080p");
            clip.setTriggerType(triggerType);
            clip.setTriggerEventId(triggerEventId);
            clip.setRetentionUntil(Instant.now().plus(defaultRetentionDays, ChronoUnit.DAYS));

            videoClipRepository.save(clip);

            log.info("Simulated video clip recorded: id={}, camera={}, trigger={}", clip.getId(), camera.getName(), triggerType);

            // Generate simulated thumbnail
            generateSimulatedThumbnail(clip);

        } catch (Exception e) {
            log.error("Failed to record simulated video clip for camera {}", camera.getId(), e);
        }
    }

    /**
     * Generate a simulated thumbnail for testing purposes.
     */
    @Async
    protected void generateSimulatedThumbnail(VideoClip clip) {
        try {
            String thumbnailPath = videoStoragePath + "/simulated/thumbnails/" + clip.getId() + "_thumb.jpg";
            clip.setThumbnailPath(thumbnailPath);
            clip.setThumbnailUrl(thumbnailPath);
            videoClipRepository.save(clip);
            log.info("Simulated thumbnail path set for clip {}: {}", clip.getId(), thumbnailPath);
        } catch (Exception e) {
            log.error("Failed to set simulated thumbnail for clip {}", clip.getId(), e);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String generateFilename(UUID cameraId, Instant timestamp) {
        String cameraShort = cameraId.toString().substring(0, 8);
        String timestampStr = timestamp.toString().replace(":", "-");
        return String.format("%s_%s.mp4", cameraShort, timestampStr);
    }

    private VideoClipDto toDto(VideoClip clip) {
        return new VideoClipDto(
                clip.getId(),
                clip.getCamera() != null ? clip.getCamera().getId() : null,
                clip.getCamera() != null ? clip.getCamera().getName() : "Unknown",
                clip.getStartTime(),
                clip.getEndTime(),
                clip.getDurationSeconds(),
                clip.getFileUrl(),
                clip.getFileSizeBytes(),
                clip.getResolution(),
                clip.getFormat(),
                clip.getTriggerType(),
                clip.getThumbnailUrl(),
                clip.getRetentionUntil(),
                clip.getArchived()
        );
    }

    /**
     * Validates stream URL format to prevent command injection attacks.
     * Only allows RTSP, HTTP, and HTTPS protocols.
     */
    private boolean isValidStreamUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // Must start with valid protocol
        if (!url.matches("^(rtsp|http|https)://.*")) {
            return false;
        }

        // Must not contain shell metacharacters or command injection attempts
        String[] dangerousChars = {";", "&", "|", "`", "$", "(", ")", "<", ">", "\n", "\r"};
        for (String dangerous : dangerousChars) {
            if (url.contains(dangerous)) {
                log.warn("Stream URL contains dangerous character: {}", dangerous);
                return false;
            }
        }

        // Validate URL structure
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.matches("rtsp|https?")) {
                return false;
            }
            // Must have a host
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }
            return true;
        } catch (java.net.URISyntaxException e) {
            log.warn("Invalid stream URL syntax: {}", url);
            return false;
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record VideoClipDto(
            UUID id,
            UUID cameraId,
            String cameraName,
            Instant startTime,
            Instant endTime,
            Integer durationSeconds,
            String fileUrl,
            Long fileSizeBytes,
            String resolution,
            String format,
            String triggerType,
            String thumbnailUrl,
            Instant retentionUntil,
            Boolean archived
    ) {}

    public record StorageStatsDto(
            long totalBytes,
            double totalGigabytes,
            long clipCount,
            long averageClipSize
    ) {}
}
