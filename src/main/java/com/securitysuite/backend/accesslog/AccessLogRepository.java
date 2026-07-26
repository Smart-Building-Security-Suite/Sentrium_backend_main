package com.securitysuite.backend.accesslog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLog, UUID> {
    Page<AccessLog> findByZoneId(UUID zoneId, Pageable pageable);
    Page<AccessLog> findByTimestampBetween(Instant start, Instant end, Pageable pageable);
    java.util.List<AccessLog> findByTimestampBetween(Instant start, Instant end);
}
