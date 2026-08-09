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
    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone WHERE i.status = :status")
    Page<Incident> findByStatus(@Param("status") IncidentStatus status, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone WHERE i.type = :type")
    Page<Incident> findByType(@Param("type") IncidentType type, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone WHERE i.severity = :severity")
    Page<Incident> findBySeverity(@Param("severity") IncidentSeverity severity, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone WHERE i.assignedTo.id = :assignedToId")
    Page<Incident> findByAssignedToId(@Param("assignedToId") UUID assignedToId, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone WHERE i.zone.id = :zoneId")
    Page<Incident> findByZoneId(@Param("zoneId") UUID zoneId, Pageable pageable);

    @Query("SELECT DISTINCT i FROM Incident i LEFT JOIN FETCH i.reportedBy LEFT JOIN FETCH i.assignedTo LEFT JOIN FETCH i.zone")
    Page<Incident> findAll(Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.reportedAt BETWEEN :start AND :end")
    List<Incident> findByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT i FROM Incident i WHERE i.status = :openStatus OR i.status = :investigatingStatus OR i.status = :inProgressStatus")
    List<Incident> findOpenIncidents(@Param("openStatus") IncidentStatus openStatus,
                                      @Param("investigatingStatus") IncidentStatus investigatingStatus,
                                      @Param("inProgressStatus") IncidentStatus inProgressStatus);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.status = :openStatus OR i.status = :investigatingStatus OR i.status = :inProgressStatus")
    long countOpenIncidents(@Param("openStatus") IncidentStatus openStatus,
                            @Param("investigatingStatus") IncidentStatus investigatingStatus,
                            @Param("inProgressStatus") IncidentStatus inProgressStatus);

    @Query("SELECT i FROM Incident i WHERE i.requiresFollowUp = true AND i.followUpDate <= :now AND i.status != :closedStatus")
    List<Incident> findDueForFollowUp(@Param("now") Instant now, @Param("closedStatus") IncidentStatus closedStatus);
}
