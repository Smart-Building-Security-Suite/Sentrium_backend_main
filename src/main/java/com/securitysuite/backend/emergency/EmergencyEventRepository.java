package com.securitysuite.backend.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EmergencyEventRepository extends JpaRepository<EmergencyEvent, UUID> {
    List<EmergencyEvent> findByStatus(EmergencyStatus status);

    @Query("SELECT e FROM EmergencyEvent e WHERE e.status = :activeStatus ORDER BY e.triggeredAt DESC")
    List<EmergencyEvent> findActiveEmergencies(@org.springframework.data.repository.query.Param("activeStatus") EmergencyStatus activeStatus);

    @Query("SELECT COUNT(e) FROM EmergencyEvent e WHERE e.status = :activeStatus")
    long countActiveEmergencies(@org.springframework.data.repository.query.Param("activeStatus") EmergencyStatus activeStatus);
}
