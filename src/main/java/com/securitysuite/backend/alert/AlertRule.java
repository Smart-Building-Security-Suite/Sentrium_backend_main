package com.securitysuite.backend.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name="alert_rules")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ruleId; // e.g. "arule_01"
    private String name;
    
    @Enumerated(EnumType.STRING)
    private com.securitysuite.backend.alert.AlertType type;
    
    private Integer threshold;
    private Integer windowSeconds;
    
    @Enumerated(EnumType.STRING)
    private com.securitysuite.backend.alert.AlertSeverity severity;
    
    private boolean enabled;
}
