package com.securitysuite.backend.analytics;

import com.securitysuite.backend.accesslog.AccessLogActivityRepository;
import com.securitysuite.backend.alert.AlertRepository;
import com.securitysuite.backend.incident.Incident;
import com.securitysuite.backend.incident.IncidentRepository;
import com.securitysuite.backend.zone.Zone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.alert.Alert;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final AccessLogActivityRepository accessLogActivityRepository;
    private final AnalyticsDailyRepository analyticsDailyRepository;

    @Transactional
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        log.info("Analytics aggregation started for {}", today);

        // Get start and end of today in UTC
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Get all incidents reported today
        List<Incident> todayIncidents = incidentRepository.findByDateRange(startOfDay, endOfDay);

        // Group incidents by zone
        Map<Zone, List<Incident>> incidentsByZone = todayIncidents.stream()
                .filter(i -> i.getZone() != null)
                .collect(Collectors.groupingBy(Incident::getZone));

        // Aggregate for each zone
        for (Map.Entry<Zone, List<Incident>> entry : incidentsByZone.entrySet()) {
            Zone zone = entry.getKey();
            List<Incident> incidents = entry.getValue();

            long incidentCount = incidents.size();

            // Calculate average resolution time in minutes (only for resolved incidents)
            double avgResolutionMins = incidents.stream()
                    .filter(i -> i.getResolvedAt() != null && i.getReportedAt() != null)
                    .mapToLong(i -> ChronoUnit.MINUTES.between(i.getReportedAt(), i.getResolvedAt()))
                    .average()
                    .orElse(0.0);

            AnalyticsDaily daily = analyticsDailyRepository.findByZoneIdAndDate(zone.getId(), today)
                    .orElseGet(AnalyticsDaily::new);
            daily.setZone(zone);
            daily.setDate(today);
            daily.setIncidentCount(incidentCount);
            daily.setAvgResolutionMins(avgResolutionMins);
            analyticsDailyRepository.save(daily);

            log.debug("Aggregated for zone {}: {} incidents, avg resolution {} mins",
                     zone.getName(), incidentCount, String.format("%.1f", avgResolutionMins));
        }

        log.info("Analytics aggregation completed for {}: {} zones processed", today, incidentsByZone.size());
    }

    public List<AnalyticsSummary> summary() {
        return analyticsDailyRepository.findAllOrderByDateAscZoneNameAsc().stream()
                .map(a -> new AnalyticsSummary(
                        a.getZone().getId(),
                        a.getZone().getName(),
                        a.getDate(),
                        a.getIncidentCount(),
                        a.getAvgResolutionMins()
                ))
                .toList();
    }


    public record AnalyticsSummary(
            java.util.UUID zoneId,
            String zoneName,
            LocalDate date,
            long incidentCount,
            double avgResolutionMins
    ) {}

    public List<TopDeviceDto> getTopDevices(String metric, int limit) {
        // For now, we'll use alerts since incidents don't have a direct device relationship
        // Incidents are zone-based, not device-based
        // This method shows top devices by alert count

        if (!"alertCount".equals(metric) && !"incidentCount".equals(metric)) {
            log.warn("Unsupported metric: {}. Supported: alertCount, incidentCount", metric);
            return List.of();
        }

        List<Alert> allAlerts = alertRepository.findAll();
        Map<Device, Long> counts = allAlerts.stream()
                .filter(a -> a.getDevice() != null)
                .collect(Collectors.groupingBy(Alert::getDevice, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<Device, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new TopDeviceDto(
                        e.getKey().getId().toString(),
                        e.getKey().getName(),
                        e.getValue().intValue()
                ))
                .toList();
    }

    /**
     * Get top zones by incident count
     */
    public List<TopZoneDto> getTopZones(int limit) {
        List<Incident> allIncidents = incidentRepository.findAll();
        Map<Zone, Long> counts = allIncidents.stream()
                .filter(i -> i.getZone() != null)
                .collect(Collectors.groupingBy(Incident::getZone, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(Map.Entry.<Zone, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new TopZoneDto(
                        e.getKey().getId(),
                        e.getKey().getName(),
                        e.getValue().intValue()
                ))
                .toList();
    }

    public record TopZoneDto(
            java.util.UUID zoneId,
            String zoneName,
            int incidentCount
    ) {}
}
