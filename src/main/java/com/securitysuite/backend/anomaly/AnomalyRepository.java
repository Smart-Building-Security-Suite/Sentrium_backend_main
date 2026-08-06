package com.securitysuite.backend.anomaly;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    Page<Anomaly> findByAnomalyType(AnomalyType type, Pageable pageable);

    Page<Anomaly> findBySeverity(AnomalySeverity severity, Pageable pageable);

    Page<Anomaly> findByReviewed(Boolean reviewed, Pageable pageable);

    @Query("SELECT a FROM Anomaly a WHERE a.reviewed = false ORDER BY a.severity DESC, a.detectedAt DESC")
    List<Anomaly> findUnreviewedOrderedBySeverity();

    @Query("SELECT a FROM Anomaly a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.detectedAt DESC")
    List<Anomaly> findByEntity(@Param("entityType") String entityType, @Param("entityId") UUID entityId);

    @Query("SELECT COUNT(a) FROM Anomaly a WHERE a.reviewed = false")
    long countUnreviewed();

    @Query("SELECT a FROM Anomaly a WHERE a.detectedAt BETWEEN :start AND :end")
    List<Anomaly> findByDateRange(@Param("start") Instant start, @Param("end") Instant end);
}
