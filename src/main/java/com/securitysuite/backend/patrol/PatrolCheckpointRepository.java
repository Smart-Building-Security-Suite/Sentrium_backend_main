package com.securitysuite.backend.patrol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatrolCheckpointRepository extends JpaRepository<PatrolCheckpoint, UUID> {
    Optional<PatrolCheckpoint> findByQrCode(String qrCode);
}
