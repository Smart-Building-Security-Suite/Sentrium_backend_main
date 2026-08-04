package com.securitysuite.backend.alert;

public record AlertRuleDto(
        Long id,
        String ruleId,
        String name,
        AlertType type,
        Integer threshold,
        Integer windowSeconds,
        AlertSeverity severity,
        boolean enabled
) {
    public static AlertRuleDto from(AlertRule rule) {
        return new AlertRuleDto(
                rule.getId(),
                rule.getRuleId(),
                rule.getName(),
                rule.getType(),
                rule.getThreshold(),
                rule.getWindowSeconds(),
                rule.getSeverity(),
                rule.isEnabled()
        );
    }
}
