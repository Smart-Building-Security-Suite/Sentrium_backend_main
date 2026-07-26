package com.securitysuite.backend.notification;

import com.securitysuite.backend.alert.Alert;

public record AlertCreatedEvent(Alert alert) {
}
