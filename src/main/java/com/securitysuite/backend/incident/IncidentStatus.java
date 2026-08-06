package com.securitysuite.backend.incident;

public enum IncidentStatus {
    OPEN,           // Reported, not yet assigned
    INVESTIGATING,  // Under investigation
    IN_PROGRESS,    // Action being taken
    RESOLVED,       // Completed
    CLOSED,         // Archived, no further action
    ESCALATED       // Escalated to higher authority
}
