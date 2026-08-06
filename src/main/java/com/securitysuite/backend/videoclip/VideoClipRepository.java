package com.securitysuite.backend.videoclip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VideoClipRepository extends JpaRepository<VideoClip, UUID> {
    Page<VideoClip> findByCameraIdOrderByStartTimeDesc(UUID cameraId, Pageable pageable);

    Page<VideoClip> findByCameraIdAndStartTimeBetween(UUID cameraId, Instant from, Instant to, Pageable pageable);

    Page<VideoClip> findByTriggerTypeOrderByStartTimeDesc(String triggerType, Pageable pageable);

    @Query("SELECT v FROM VideoClip v WHERE v.retentionUntil < :now AND v.archived = false")
    List<VideoClip> findExpiredClips(Instant now);

    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM VideoClip v WHERE v.archived = false")
    Long getTotalStorageUsed();

    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM VideoClip v WHERE v.camera.id = :cameraId AND v.archived = false")
    Long getStorageUsedByCamera(UUID cameraId);
}
