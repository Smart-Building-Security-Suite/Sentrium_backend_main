package com.securitysuite.backend.anomaly;

public enum AnomalyType {
    RAPID_ACCESS_ATTEMPTS,        // Multiple doors in short time
    AFTER_HOURS_ACCESS,           // Access outside normal schedule
    UNUSUAL_LOCATION_PATTERN,     // User accessing unusual zones
    FAILED_ACCESS_SPIKE,          // Multiple failed attempts
    TAILGATING_SUSPECTED,         // Multiple entries on single badge scan
    BADGE_SHARING_SUSPECTED,      // Same badge used in distant locations
    EXCESSIVE_DWELLING_TIME,      // User staying in zone too long
    UNAUTHORIZED_ZONE_ACCESS,     // Access to restricted zone
    DEVICE_TAMPERING,             // Device reporting abnormal metrics
    MASS_ACCESS_ANOMALY,          // Unusual number of people entering
    CREDENTIAL_REUSE,             // Deactivated credential still in use
    GEOFENCING_VIOLATION,         // Mobile access used outside permitted area
    TIME_IMPOSSIBLE_TRAVEL,       // Physical travel time violation
    OTHER
}
