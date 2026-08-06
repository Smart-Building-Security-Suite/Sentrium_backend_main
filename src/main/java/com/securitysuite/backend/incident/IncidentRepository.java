package com.securitysuite.backend.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findByType(IncidentType type, Pageable pageable);

    Page<Incident> findBySeverity(IncidentSeverity severity, Pageable pageable);

    Page<Incident> findByAssignedToId(UUID assignedToId, Pageable pageable);

    Page<Incident> findByZoneId(UUID zoneId, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.reportedAt BETWEEN :start AND :end")
    List<Incident> findByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')")
    List<Incident> findOpenIncidents();

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')")
    long countOpenIncidents();

    @Query("SELECT i FROM Incident i WHERE i.requiresFollowUp = true AND i.followUpDate <= :now AND i.status != 'CLOSED'")
    List<Incident> findDueForFollowUp(@Param("now") Instant now);
}
