package com.securitysuite.backend.patrol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, UUID> {
    List<PatrolRoute> findByEnabledTrue();
}
