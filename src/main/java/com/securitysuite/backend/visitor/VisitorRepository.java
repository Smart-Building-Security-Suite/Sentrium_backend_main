package com.securitysuite.backend.visitor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, UUID> {
    Page<Visitor> findByStatus(VisitorStatus status, Pageable pageable);

    Page<Visitor> findByHostId(UUID hostId, Pageable pageable);

    @Query("SELECT v FROM Visitor v WHERE v.status = 'CHECKED_IN'")
    List<Visitor> findCurrentlyOnPremises();

    @Query("SELECT v FROM Visitor v WHERE v.expectedArrivalAt BETWEEN :start AND :end")
    List<Visitor> findExpectedVisitors(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT v FROM Visitor v WHERE v.status = 'PRE_REGISTERED' AND v.expectedArrivalAt < :now")
    List<Visitor> findOverdueVisitors(@Param("now") Instant now);

    @Query("SELECT COUNT(v) FROM Visitor v WHERE v.status = 'CHECKED_IN'")
    long countCurrentVisitors();
}
