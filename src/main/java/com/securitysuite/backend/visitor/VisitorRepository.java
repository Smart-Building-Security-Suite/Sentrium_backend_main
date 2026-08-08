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

    @Query("SELECT v FROM Visitor v WHERE v.status = :checkedInStatus")
    List<Visitor> findCurrentlyOnPremises(@Param("checkedInStatus") VisitorStatus checkedInStatus);

    @Query("SELECT v FROM Visitor v WHERE v.expectedArrivalAt BETWEEN :start AND :end")
    List<Visitor> findExpectedVisitors(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT v FROM Visitor v WHERE v.status = :preRegisteredStatus AND v.expectedArrivalAt < :now")
    List<Visitor> findOverdueVisitors(@Param("now") Instant now, @Param("preRegisteredStatus") VisitorStatus preRegisteredStatus);

    @Query("SELECT COUNT(v) FROM Visitor v WHERE v.status = :checkedInStatus")
    long countCurrentVisitors(@Param("checkedInStatus") VisitorStatus checkedInStatus);
}
