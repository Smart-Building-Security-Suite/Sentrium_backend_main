package com.securitysuite.backend.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/incidents-summary")
    @Operation(summary = "Get incidents analytics summary",
               description = "Returns aggregated incident statistics including counts by status, type, and severity. Used for dashboard analytics and reporting. Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public List<AnalyticsService.AnalyticsSummary> summary() {
        return analyticsService.summary();
    }

    @GetMapping("/top-devices")
    @Operation(summary = "Get top devices by alert count",
               description = "Returns the top devices ranked by alert count. Limit controls how many results are returned (default 10). Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public List<TopDeviceDto> topDevices(@RequestParam(defaultValue = "alertCount") String metric,
                                         @RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopDevices(metric, limit);
    }

    @GetMapping("/top-zones")
    @Operation(summary = "Get top zones by incident count",
               description = "Returns the top zones ranked by incident count. Limit controls how many results are returned (default 10). Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public List<AnalyticsService.TopZoneDto> topZones(@RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopZones(limit);
    }

    @PostMapping("/aggregate-now")
    @Operation(summary = "Manually trigger analytics aggregation",
               description = "Triggers the daily analytics aggregation job immediately. Normally runs at midnight. Useful for testing or manual data refresh. Admin access only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> aggregateNow() {
        analyticsService.aggregateToday();
        return ResponseEntity.ok("Analytics aggregation completed");
    }
}
