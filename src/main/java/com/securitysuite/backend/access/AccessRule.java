package com.securitysuite.backend.access;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity 
@Table(name="access_rules")
@Getter
@Setter
@NoArgsConstructor
public class AccessRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ruleId; // e.g. "rule_01" (unique string identifier for API)
    private String doorId;
    private String requiredLevel;
    @ElementCollection @CollectionTable(name="access_rule_roles")
    private List<String> allowedRoles;
}
