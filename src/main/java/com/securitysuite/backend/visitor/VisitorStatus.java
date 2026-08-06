package com.securitysuite.backend.visitor;

public enum VisitorStatus {
    PRE_REGISTERED,  // Visitor scheduled but hasn't arrived
    CHECKED_IN,      // Currently on premises
    CHECKED_OUT,     // Visit completed
    NO_SHOW,         // Expected but never arrived
    DENIED,          // Access denied at check-in
    CANCELLED        // Pre-registration cancelled
}
