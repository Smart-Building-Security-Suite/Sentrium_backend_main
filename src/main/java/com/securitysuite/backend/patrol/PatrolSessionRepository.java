package com.securitysuite.backend.patrol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolSessionRepository extends JpaRepository<PatrolSession, UUID> {
    List<PatrolSession> findByOfficerId(UUID officerId);

    List<PatrolSession> findByStatus(PatrolSessionStatus status);

    @Query("SELECT ps FROM PatrolSession ps WHERE ps.officer.id = :officerId AND ps.status = 'IN_PROGRESS'")
    Optional<PatrolSession> findActiveSessionByOfficer(@Param("officerId") UUID officerId);
}
