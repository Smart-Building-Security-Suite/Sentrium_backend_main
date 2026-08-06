package com.securitysuite.backend.videoclip;

import com.securitysuite.backend.device.Device;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_clip")
@Getter
@Setter
@NoArgsConstructor
public class VideoClip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "camera_id")
    private Device camera;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_url")
    private String fileUrl; // Public URL (S3, CDN)

    @Column(name = "file_path")
    private String filePath; // Local filesystem path

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "resolution")
    private String resolution; // "1080p", "720p"

    @Column(name = "format")
    private String format = "MP4"; // MP4, MKV, etc.

    @Column(name = "trigger_type")
    private String triggerType; // MOTION, ALERT, MANUAL, SCHEDULED

    @Column(name = "trigger_event_id")
    private UUID triggerEventId; // motion_event.id or alert.id

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(nullable = false)
    private Boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(columnDefinition = "jsonb")
    private String metadata; // JSON metadata (codec, bitrate, etc.)
}
