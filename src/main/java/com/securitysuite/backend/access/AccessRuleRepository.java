package com.securitysuite.backend.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessRuleRepository extends JpaRepository<AccessRule, Long> {
}
