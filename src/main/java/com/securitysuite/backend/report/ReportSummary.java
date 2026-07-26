package com.securitysuite.backend.report;

import java.time.LocalDate;
import java.util.UUID;

public record ReportSummary(
        UUID id,
        UUID requestedById,
        String requestedByEmail,
        ReportType type,
        ReportFormat format,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        String fileUrl
) {
    public static ReportSummary from(Report report) {
        return new ReportSummary(
                report.getId(),
                report.getRequestedBy().getId(),
                report.getRequestedBy().getEmail(),
                report.getType(),
                report.getFormat(),
                report.getRangeStart(),
                report.getRangeEnd(),
                report.getFileUrl()
        );
    }
}
