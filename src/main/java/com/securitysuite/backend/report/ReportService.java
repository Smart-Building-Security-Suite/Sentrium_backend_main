package com.securitysuite.backend.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.securitysuite.backend.accesslog.AccessLog;
import com.securitysuite.backend.accesslog.AccessLogRepository;
import com.securitysuite.backend.alert.Alert;
import com.securitysuite.backend.alert.AlertRepository;
import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final AccessLogRepository accessLogRepository;
    private final AlertRepository alertRepository;
    private final UserService userService;

    @Value("${app.reports-dir:reports}")
    private String reportsDir;

    @Transactional
    public Report generate(ReportRequest request, String requesterPhoneNumber) {
        if (request.rangeEnd().isBefore(request.rangeStart())) {
            throw new IllegalArgumentException("rangeEnd: must be on or after rangeStart");
        }
        User requester = userService.getByPhoneNumber(requesterPhoneNumber);
        Report report = new Report();
        report.setRequestedBy(requester);
        report.setType(request.type());
        report.setFormat(request.format());
        report.setRangeStart(request.rangeStart());
        report.setRangeEnd(request.rangeEnd());
        report = reportRepository.save(report);

        try {
            Files.createDirectories(Path.of(reportsDir));
            String ext = request.format() == ReportFormat.CSV ? ".csv" : ".pdf";
            Path file = Path.of(reportsDir, report.getId() + ext);
            if (request.type() == ReportType.ACCESS_LOG) {
                List<AccessLog> rows = accessLogRepository.findByTimestampBetween(start(request.rangeStart()), end(request.rangeEnd()));
                if (request.format() == ReportFormat.CSV) writeAccessLogsCsv(file, rows); else writeAccessLogsPdf(file, rows);
            } else {
                List<Alert> rows = alertRepository.findByCreatedAtBetween(start(request.rangeStart()), end(request.rangeEnd()));
                if (request.format() == ReportFormat.CSV) writeAlertsCsv(file, rows); else writeAlertsPdf(file, rows);
            }
            report.setFileUrl(file.toString());
            // Local disk is fine for this student project, but Render redeploys can remove files; use S3/R2 for durable storage later.
            return report;
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("Failed to generate report");
        }
    }

    public List<Report> listForUser(String phoneNumber) {
        return reportRepository.findByRequestedByPhoneNumber(phoneNumber);
    }

    public Resource loadFile(UUID id, String requesterPhoneNumber) {
        Report report = reportRepository.findById(id).orElseThrow(() -> new NotFoundException("Report not found"));
        if (!report.getRequestedBy().getPhoneNumber().equals(requesterPhoneNumber)) {
            throw new NotFoundException("Report not found");
        }
        if (report.getFileUrl() == null) {
            throw new NotFoundException("Report file not generated");
        }
        Path path = Path.of(report.getFileUrl());
        if (!Files.exists(path)) {
            throw new NotFoundException("Report file missing");
        }
        return new FileSystemResource(path);
    }

    private Instant start(java.time.LocalDate date) { return date.atStartOfDay().toInstant(ZoneOffset.UTC); }
    private Instant end(java.time.LocalDate date) { return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC); }

    private void writeAccessLogsCsv(Path file, List<AccessLog> rows) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("id","userPhoneNumber","deviceName","zoneName","result","timestamp").build();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8), format)) {
            for (AccessLog row : rows) printer.printRecord(row.getId(), row.getUser().getPhoneNumber(), row.getDevice().getName(), row.getZone().getName(), row.getResult(), row.getTimestamp());
        }
    }

    private void writeAlertsCsv(Path file, List<Alert> rows) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("id","zoneName","deviceName","severity","status","message","createdAt","resolvedAt").build();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8), format)) {
            for (Alert row : rows) printer.printRecord(row.getId(), row.getZone().getName(), row.getDevice() == null ? "" : row.getDevice().getName(), row.getSeverity(), row.getStatus(), row.getMessage(), row.getCreatedAt(), row.getResolvedAt());
        }
    }

    private void writeAccessLogsPdf(Path file, List<AccessLog> rows) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, Files.newOutputStream(file));
        try {
            document.open();
            document.add(new Paragraph("Access Log Report"));
            for (AccessLog row : rows) document.add(new Paragraph(row.getTimestamp() + " | " + row.getUser().getPhoneNumber() + " | " + row.getZone().getName() + " | " + row.getResult()));
        } finally {
            document.close();
        }
    }

    private void writeAlertsPdf(Path file, List<Alert> rows) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, Files.newOutputStream(file));
        try {
            document.open();
            document.add(new Paragraph("Alert History Report"));
            for (Alert row : rows) document.add(new Paragraph(row.getCreatedAt() + " | " + row.getZone().getName() + " | " + row.getSeverity() + " | " + row.getStatus() + " | " + row.getMessage()));
        } finally {
            document.close();
        }
    }

    public record ReportRequest(ReportType type, ReportFormat format, java.time.LocalDate rangeStart, java.time.LocalDate rangeEnd) {}
}
