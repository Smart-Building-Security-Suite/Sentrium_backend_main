package com.securitysuite.backend.analytics;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/analytics/incidents-summary")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public List<AnalyticsService.AnalyticsSummary> summary() {
        return analyticsService.summary();
    }
}
