package com.securitysuite.backend.alert;

import com.securitysuite.backend.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alerts/rules")
@RequiredArgsConstructor
@Tag(name = "Alert Rules")
public class AlertRuleController {
    private final AlertRuleRepository repository;

    @GetMapping
    @Operation(summary = "List all alert rules",
               description = "Retrieves all automated alert rules that define when and how alerts are triggered based on system events, thresholds, and time windows.")
    public List<AlertRuleDto> list() {
        return repository.findAll().stream().map(AlertRuleDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new alert rule",
               description = "Defines a new automated alert rule with type, threshold, time window, and severity. Rules are evaluated by the system to generate alerts automatically. Enabled by default. Admin only.")
    public ResponseEntity<AlertRuleDto> create(@Valid @RequestBody AlertRuleRequest request) {
        AlertRule rule = new AlertRule();
        rule.setRuleId(request.ruleId());
        rule.setName(request.name());
        rule.setType(request.type());
        rule.setThreshold(request.threshold());
        rule.setWindowSeconds(request.windowSeconds());
        rule.setSeverity(request.severity());
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        } else {
            rule.setEnabled(true); // default to true if not specified?
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertRuleDto.from(repository.save(rule)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an alert rule",
               description = "Modifies an existing alert rule. Can update thresholds, time windows, severity, or enable/disable the rule. Admin only.")
    public AlertRuleDto update(@PathVariable Long id, @RequestBody AlertRuleRequest request) {
        AlertRule rule = repository.findById(id).orElseThrow(() -> new NotFoundException("Alert rule not found"));
        if (request.ruleId() != null) rule.setRuleId(request.ruleId());
        if (request.name() != null) rule.setName(request.name());
        if (request.type() != null) rule.setType(request.type());
        if (request.threshold() != null) rule.setThreshold(request.threshold());
        if (request.windowSeconds() != null) rule.setWindowSeconds(request.windowSeconds());
        if (request.severity() != null) rule.setSeverity(request.severity());
        if (request.enabled() != null) rule.setEnabled(request.enabled());
        return AlertRuleDto.from(repository.save(rule));
    }

    public record AlertRuleRequest(
            @NotBlank String ruleId,
            String name,
            AlertType type,
            Integer threshold,
            Integer windowSeconds,
            AlertSeverity severity,
            Boolean enabled
    ) {}
}
