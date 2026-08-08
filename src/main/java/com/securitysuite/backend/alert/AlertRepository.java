package com.securitysuite.backend.alert;

import com.securitysuite.backend.zone.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Alert> {
    List<Alert> findByStatusAndSeverity(AlertStatus status, AlertSeverity severity);
    List<Alert> findByStatus(AlertStatus status);
    List<Alert> findBySeverity(AlertSeverity severity);
    List<Alert> findByCreatedAtBetween(Instant start, Instant end);

    @Query("select count(a) from Alert a where a.zone = :zone and function('date', a.createdAt) = :date")
    long countByZoneAndCreatedAtDate(@Param("zone") Zone zone, @Param("date") LocalDate date);

    @Query(value = "select coalesce(avg(extract(epoch from (resolved_at - created_at)) / 60.0), 0) from alert where zone_id = :zoneId and resolved_at is not null and cast(created_at as date) = :date", nativeQuery = true)
    Double averageResolutionMinutes(@Param("zoneId") UUID zoneId, @Param("date") LocalDate date);

    @Query("select distinct a.zone from Alert a where function('date', a.createdAt) = :date")
    List<Zone> zonesWithActivityToday(@Param("date") LocalDate date);

    @Query("select a from Alert a where a.status in ('OPEN', 'ACKNOWLEDGED') order by a.createdAt desc")
    List<Alert> findRecentOpenAlerts(org.springframework.data.domain.Pageable pageable);

    // Additional filtering methods
    List<Alert> findByZoneId(UUID zoneId);
    List<Alert> findByDeviceId(UUID deviceId);
    List<Alert> findByZoneIdAndStatus(UUID zoneId, AlertStatus status);
    List<Alert> findByZoneIdAndSeverity(UUID zoneId, AlertSeverity severity);
    List<Alert> findByDeviceIdAndStatus(UUID deviceId, AlertStatus status);
}
