package com.securitysuite.backend.access;

import java.util.List;

public record AccessRuleDto(
        Long id,
        String ruleId,
        String doorId,
        String requiredLevel,
        List<String> allowedRoles
) {
    public static AccessRuleDto from(AccessRule rule) {
        return new AccessRuleDto(
                rule.getId(),
                rule.getRuleId(),
                rule.getDoorId(),
                rule.getRequiredLevel(),
                rule.getAllowedRoles()
        );
    }
}
