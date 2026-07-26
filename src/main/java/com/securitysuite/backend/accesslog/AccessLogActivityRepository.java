package com.securitysuite.backend.accesslog;

import com.securitysuite.backend.zone.Zone;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AccessLogActivityRepository extends Repository<AccessLog, java.util.UUID> {
    @Query("select distinct a.zone from AccessLog a where function('date', a.timestamp) = :date")
    List<Zone> zonesWithActivityToday(@Param("date") LocalDate date);
}
