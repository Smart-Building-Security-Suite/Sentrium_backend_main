package com.securitysuite.backend.analytics;

import com.securitysuite.backend.accesslog.AccessLogActivityRepository;
import com.securitysuite.backend.alert.AlertRepository;
import com.securitysuite.backend.zone.Zone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final AccessLogActivityRepository accessLogActivityRepository;
    private final AnalyticsDailyRepository analyticsDailyRepository;

    @Transactional
    public void aggregateToday() {
        LocalDate today = LocalDate.now();
        log.info("Analytics aggregation started for {}", today);
        LinkedHashMap<java.util.UUID, Zone> zonesById = new LinkedHashMap<>();
        alertRepository.zonesWithActivityToday(today).forEach(zone -> zonesById.put(zone.getId(), zone));
        accessLogActivityRepository.zonesWithActivityToday(today).forEach(zone -> zonesById.put(zone.getId(), zone));
        for (Zone zone : zonesById.values()) {
            long count = alertRepository.countByZoneAndCreatedAtDate(zone, today);
            double avg = alertRepository.averageResolutionMinutes(zone.getId(), today);
            AnalyticsDaily daily = analyticsDailyRepository.findByZoneIdAndDate(zone.getId(), today).orElseGet(AnalyticsDaily::new);
            daily.setZone(zone);
            daily.setDate(today);
            daily.setIncidentCount(count);
            daily.setAvgResolutionMins(avg);
            analyticsDailyRepository.save(daily);
        }
        log.info("Analytics aggregation completed for {}", today);
    }

    public List<AnalyticsSummary> summary() {
        return analyticsDailyRepository.findAllOrderByDateAscZoneNameAsc().stream()
                .map(a -> new AnalyticsSummary(a.getZone().getName(), a.getDate(), a.getIncidentCount(), a.getAvgResolutionMins()))
                .toList();
    }


    public record AnalyticsSummary(String zone, LocalDate date, long incidentCount, double avgResolutionMins) {}

    public List<TopDeviceDto> getTopDevices(String metric, int limit) {
        if (!"incidentCount".equals(metric)) {
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
}
