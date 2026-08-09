package com.securitysuite.backend.patrol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, UUID> {
    @Query("SELECT DISTINCT r FROM PatrolRoute r LEFT JOIN FETCH r.checkpoints")
    List<PatrolRoute> findAllWithCheckpoints();

    @Query("SELECT DISTINCT r FROM PatrolRoute r LEFT JOIN FETCH r.checkpoints WHERE r.enabled = true")
    List<PatrolRoute> findByEnabledTrue();
}
