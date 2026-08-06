package com.securitysuite.backend.patrol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatrolCheckpointScanRepository extends JpaRepository<PatrolCheckpointScan, UUID> {
    List<PatrolCheckpointScan> findBySessionId(UUID sessionId);
}
