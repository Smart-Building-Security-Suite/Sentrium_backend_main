package com.securitysuite.backend.surveillance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface MotionEventRepository extends JpaRepository<MotionEvent, Long> {

    Page<MotionEvent> findByCameraIdAndDetectedAtBetween(
            String cameraId, Instant from, Instant to, Pageable pageable);

    Page<MotionEvent> findByCameraId(String cameraId, Pageable pageable);

    Page<MotionEvent> findByDetectedAtBetween(Instant from, Instant to, Pageable pageable);
}
