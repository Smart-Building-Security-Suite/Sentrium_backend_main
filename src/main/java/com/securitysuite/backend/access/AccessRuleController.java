package com.securitysuite.backend.access;

import com.securitysuite.backend.common.NotFoundException;
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
@RequestMapping("/access/rules")
@RequiredArgsConstructor
@Tag(name = "Access Rules")
public class AccessRuleController {
    private final AccessRuleRepository repository;

    @GetMapping
    public List<AccessRuleDto> list() {
        return repository.findAll().stream().map(AccessRuleDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccessRuleDto> create(@Valid @RequestBody AccessRuleRequest request) {
        AccessRule rule = new AccessRule();
        rule.setRuleId(request.ruleId());
        rule.setDoorId(request.doorId());
        rule.setRequiredLevel(request.requiredLevel());
        rule.setAllowedRoles(request.allowedRoles());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccessRuleDto.from(repository.save(rule)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccessRuleDto update(@PathVariable Long id, @RequestBody AccessRuleRequest request) {
        AccessRule rule = repository.findById(id).orElseThrow(() -> new NotFoundException("Access rule not found"));
        if (request.ruleId() != null) rule.setRuleId(request.ruleId());
        if (request.doorId() != null) rule.setDoorId(request.doorId());
        if (request.requiredLevel() != null) rule.setRequiredLevel(request.requiredLevel());
        if (request.allowedRoles() != null) rule.setAllowedRoles(request.allowedRoles());
        return AccessRuleDto.from(repository.save(rule));
    }

    public record AccessRuleRequest(
            @NotBlank String ruleId,
            String doorId,
            String requiredLevel,
            List<String> allowedRoles
    ) {}
}
