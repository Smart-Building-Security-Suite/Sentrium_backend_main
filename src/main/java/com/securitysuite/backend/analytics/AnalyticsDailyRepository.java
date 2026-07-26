package com.securitysuite.backend.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsDailyRepository extends JpaRepository<AnalyticsDaily, UUID> {
    Optional<AnalyticsDaily> findByZoneIdAndDate(UUID zoneId, LocalDate date);

    /**
     * Returns all analytics rows ordered by date ascending, then zone name ascending.
     * Uses an explicit JPQL @Query because Spring Data cannot derive sorts on nested
     * association properties (zone.name) from method names alone.
     */
    @Query("SELECT a FROM AnalyticsDaily a ORDER BY a.date ASC, a.zone.name ASC")
    List<AnalyticsDaily> findAllOrderByDateAscZoneNameAsc();
}
