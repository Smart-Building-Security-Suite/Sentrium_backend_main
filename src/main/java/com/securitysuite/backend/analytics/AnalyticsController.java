package com.securitysuite.backend.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Get top devices by metric",
               description = "Returns the top devices ranked by specified metric (incidentCount, alertCount, accessDeniedCount, etc.). Limit controls how many results are returned (default 10). Admin and Security Officer access.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public List<TopDeviceDto> topDevices(@RequestParam(defaultValue = "incidentCount") String metric,
                                         @RequestParam(defaultValue = "10") int limit) {
        return analyticsService.getTopDevices(metric, limit);
    }
}
