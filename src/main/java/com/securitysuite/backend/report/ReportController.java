package com.securitysuite.backend.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<ReportSummary> create(@Valid @RequestBody ReportRequestBody request, @AuthenticationPrincipal UserDetails principal) {
        Report report = reportService.generate(new ReportService.ReportRequest(request.type(), request.format(), request.rangeStart(), request.rangeEnd()), principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportSummary.from(report));
    }

    @GetMapping
    public List<ReportSummary> list(@AuthenticationPrincipal UserDetails principal) {
        return reportService.listForUser(principal.getUsername()).stream().map(ReportSummary::from).toList();
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a generated report file")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal UserDetails principal) {
        Resource resource = reportService.loadFile(id, principal.getUsername());
        String filename = resource.getFilename() == null ? "report" : resource.getFilename();
        MediaType mediaType = filename.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    public record ReportRequestBody(@NotNull ReportType type, @NotNull ReportFormat format, @NotNull LocalDate rangeStart, @NotNull LocalDate rangeEnd) {}
}
