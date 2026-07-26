package com.securitysuite.backend.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyticsAggregationJob {
    private final AnalyticsService analyticsService;

    @Scheduled(cron = "0 0 0 * * *") // Daily aggregation at midnight
    public void run() {
        analyticsService.aggregateToday();
    }
}
