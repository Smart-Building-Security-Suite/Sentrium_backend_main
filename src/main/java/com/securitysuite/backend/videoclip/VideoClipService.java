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
        Device camera = deviceRepository.findById(cameraId)
                .orElseThrow(() -> new NotFoundException("Camera not found"));

        if (camera.getStreamUrl() == null || camera.getStreamUrl().isBlank()) {
            log.warn("Cannot record clip: camera {} has no stream URL configured", cameraId);
            return;
        }

        Instant startTime = Instant.now();
        String filename = generateFilename(cameraId, startTime);
        String outputPath = videoStoragePath + "/" + filename;

        // Ensure storage directory exists
        new File(videoStoragePath).mkdirs();

        log.info("Starting recording: camera={}, duration={}s, output={}", cameraId, durationSeconds, outputPath);

        try {
            // Build FFmpeg command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-rtsp_transport", "tcp",
                    "-i", camera.getStreamUrl(),
                    "-t", String.valueOf(durationSeconds),
                    "-c", "copy", // Copy codec (no re-encoding)
                    "-movflags", "+faststart", // Optimize for web playback
                    "-y", // Overwrite output file
                    outputPath
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

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
                log.error("FFmpeg recording timeout for camera {}", cameraId);
                return;
            }

            if (process.exitValue() != 0) {
                log.error("FFmpeg recording failed with exit code {} for camera {}", process.exitValue(), cameraId);
                return;
            }

            // Get file size
            File videoFile = new File(outputPath);
            if (!videoFile.exists()) {
                log.error("Video file not created: {}", outputPath);
                return;
            }

            long fileSize = videoFile.length();
            Instant endTime = Instant.now();

            // Create video clip record
            VideoClip clip = new VideoClip();
            clip.setCamera(camera);
            clip.setStartTime(startTime);
            clip.setEndTime(endTime);
            clip.setDurationSeconds(durationSeconds);
            clip.setFilePath(outputPath);
            clip.setFileSizeBytes(fileSize);
            clip.setFormat("MP4");
            clip.setResolution(camera.getStreamResolution() != null ? camera.getStreamResolution() : "Unknown");
            clip.setTriggerType(triggerType);
            clip.setTriggerEventId(triggerEventId);
            clip.setRetentionUntil(Instant.now().plus(defaultRetentionDays, ChronoUnit.DAYS));

            videoClipRepository.save(clip);

            log.info("Video clip recorded successfully: id={}, size={}MB", clip.getId(), fileSize / 1024 / 1024);

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
                    videoClipRepository.save(clip);
                    log.info("Thumbnail generated for clip {}", clipId);
                }
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
