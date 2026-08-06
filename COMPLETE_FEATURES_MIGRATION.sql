-- ========================================================================
-- SENTRIUM SMART BUILDING SECURITY SUITE - COMPLETE FEATURES MIGRATION
-- All 22 Features Database Schema
-- ========================================================================

-- ========================================================================
-- 1. VISITOR MANAGEMENT SYSTEM
-- ========================================================================
CREATE TABLE IF NOT EXISTS visitor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    company VARCHAR(100),
    purpose VARCHAR(500),
    host_user_id UUID REFERENCES "user"(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PRE_REGISTERED',
    expected_arrival_at TIMESTAMP,
    expected_departure_at TIMESTAMP,
    checked_in_at TIMESTAMP,
    checked_out_at TIMESTAMP,
    badge_number VARCHAR(50),
    photo_url VARCHAR(500),
    id_document_url VARCHAR(500),
    vehicle_plate_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id UUID REFERENCES "user"(id),
    notes VARCHAR(1000),
    host_notified BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_visitor_status ON visitor(status);
CREATE INDEX idx_visitor_host ON visitor(host_user_id);
CREATE INDEX idx_visitor_expected_arrival ON visitor(expected_arrival_at);

-- ========================================================================
-- 2. INCIDENT MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS incident (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    zone_id UUID REFERENCES zone(id),
    location VARCHAR(200),
    reported_by_user_id UUID NOT NULL REFERENCES "user"(id),
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_to_user_id UUID REFERENCES "user"(id),
    occurred_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution VARCHAR(2000),
    actions_taken VARCHAR(2000),
    requires_follow_up BOOLEAN NOT NULL DEFAULT false,
    follow_up_date TIMESTAMP,
    tags VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS incident_evidence (
    incident_id UUID REFERENCES incident(id) ON DELETE CASCADE,
    file_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS incident_involved_parties (
    incident_id UUID REFERENCES incident(id) ON DELETE CASCADE,
    party_name VARCHAR(200)
);

CREATE INDEX idx_incident_status ON incident(status);
CREATE INDEX idx_incident_type ON incident(type);
CREATE INDEX idx_incident_severity ON incident(severity);
CREATE INDEX idx_incident_assigned_to ON incident(assigned_to_user_id);
CREATE INDEX idx_incident_zone ON incident(zone_id);
CREATE INDEX idx_incident_reported_at ON incident(reported_at DESC);

-- ========================================================================
-- 3. ACCESS SCHEDULES
-- ========================================================================
CREATE TABLE IF NOT EXISTS access_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    zone_id UUID REFERENCES zone(id),
    device_id UUID REFERENCES device(id),
    day_of_week INT, -- 1=Monday, 7=Sunday, NULL=all days
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    valid_from DATE,
    valid_until DATE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS access_schedule_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id UUID REFERENCES access_schedule(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    is_holiday BOOLEAN NOT NULL DEFAULT true,
    reason VARCHAR(200)
);

CREATE INDEX idx_access_schedule_zone ON access_schedule(zone_id);
CREATE INDEX idx_access_schedule_device ON access_schedule(device_id);

-- ========================================================================
-- 4. BADGE/CREDENTIAL MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS credential (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_number VARCHAR(50) NOT NULL UNIQUE,
    credential_type VARCHAR(50) NOT NULL, -- BADGE, KEY_FOB, MOBILE_QR, BIOMETRIC
    user_id UUID REFERENCES "user"(id),
    visitor_id UUID REFERENCES visitor(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, LOST, STOLEN, EXPIRED
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    deactivated_at TIMESTAMP,
    deactivation_reason VARCHAR(500),
    access_level VARCHAR(50),
    notes VARCHAR(1000)
);

CREATE INDEX idx_credential_number ON credential(credential_number);
CREATE INDEX idx_credential_user ON credential(user_id);
CREATE INDEX idx_credential_status ON credential(status);

-- ========================================================================
-- 5. SHIFT MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS security_shift (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    shift_type VARCHAR(50) NOT NULL, -- DAY, NIGHT, SWING
    assigned_user_id UUID REFERENCES "user"(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, ACTIVE, COMPLETED, MISSED
    check_in_time TIMESTAMP,
    check_out_time TIMESTAMP,
    handover_notes VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shift_assigned_user ON security_shift(assigned_user_id);
CREATE INDEX idx_shift_start_time ON security_shift(start_time);
CREATE INDEX idx_shift_status ON security_shift(status);

-- ========================================================================
-- 6. PATROL/ROUNDS MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS patrol_route (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    estimated_duration_minutes INT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS patrol_checkpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID REFERENCES patrol_route(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(200),
    zone_id UUID REFERENCES zone(id),
    sequence_order INT NOT NULL,
    qr_code VARCHAR(200) UNIQUE,
    required BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS patrol_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID REFERENCES patrol_route(id),
    officer_user_id UUID NOT NULL REFERENCES "user"(id),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, ABORTED
    notes VARCHAR(2000)
);

CREATE TABLE IF NOT EXISTS patrol_checkpoint_scan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES patrol_session(id) ON DELETE CASCADE,
    checkpoint_id UUID REFERENCES patrol_checkpoint(id),
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    incident_reported BOOLEAN NOT NULL DEFAULT false,
    incident_id UUID REFERENCES incident(id),
    notes VARCHAR(1000)
);

CREATE INDEX idx_patrol_session_officer ON patrol_session(officer_user_id);
CREATE INDEX idx_patrol_session_route ON patrol_session(route_id);

-- ========================================================================
-- 7. EMERGENCY RESPONSE SYSTEM
-- ========================================================================
CREATE TABLE IF NOT EXISTS emergency_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL, -- LOCKDOWN, EVACUATION, FIRE, MEDICAL, ACTIVE_THREAT
    severity VARCHAR(20) NOT NULL, -- CRITICAL, HIGH
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, RESOLVED, CANCELLED
    triggered_by_user_id UUID REFERENCES "user"(id),
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    affected_zones TEXT, -- JSON array of zone IDs
    description VARCHAR(2000),
    response_actions VARCHAR(2000),
    all_clear_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emergency_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    role VARCHAR(100),
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    priority INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_emergency_event_status ON emergency_event(status);
CREATE INDEX idx_emergency_event_triggered_at ON emergency_event(triggered_at DESC);

-- ========================================================================
-- 8. VIDEO CLIP MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS video_clip (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id UUID REFERENCES device(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_seconds INT,
    file_url VARCHAR(500),
    file_size_mb DECIMAL(10,2),
    resolution VARCHAR(20),
    trigger_type VARCHAR(50), -- MOTION, ALERT, MANUAL, SCHEDULED
    trigger_event_id UUID, -- alert_id or incident_id
    thumbnail_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_until TIMESTAMP,
    archived BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_video_clip_camera ON video_clip(camera_id);
CREATE INDEX idx_video_clip_start_time ON video_clip(start_time DESC);
CREATE INDEX idx_video_clip_trigger_type ON video_clip(trigger_type);

-- ========================================================================
-- 9. OCCUPANCY TRACKING
-- ========================================================================
CREATE TABLE IF NOT EXISTS occupancy_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID NOT NULL REFERENCES zone(id),
    current_count INT NOT NULL DEFAULT 0,
    capacity_limit INT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zone_capacity_config (
    zone_id UUID PRIMARY KEY REFERENCES zone(id),
    max_capacity INT NOT NULL,
    warning_threshold_percentage INT DEFAULT 80,
    alert_on_exceed BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_occupancy_zone ON occupancy_snapshot(zone_id);
CREATE INDEX idx_occupancy_timestamp ON occupancy_snapshot(timestamp DESC);

-- ========================================================================
-- 10. MOBILE ACCESS (QR CODE)
-- ========================================================================
CREATE TABLE IF NOT EXISTS mobile_access_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    qr_code_data VARCHAR(500) NOT NULL UNIQUE,
    device_id UUID REFERENCES device(id),
    zone_id UUID REFERENCES zone(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    uses_remaining INT, -- NULL = unlimited
    used_count INT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_mobile_token_user ON mobile_access_token(user_id);
CREATE INDEX idx_mobile_token_qr ON mobile_access_token(qr_code_data);
CREATE INDEX idx_mobile_token_expires ON mobile_access_token(expires_at);

-- ========================================================================
-- 11. AUDIT & COMPLIANCE
-- ========================================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL, -- USER, DEVICE, ALERT, ACCESS_LOG, etc.
    entity_id VARCHAR(100),
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, ACCESS, LOGIN, LOGOUT
    performed_by_user_id UUID REFERENCES "user"(id),
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    changes_json TEXT, -- JSON of before/after values
    success BOOLEAN NOT NULL DEFAULT true,
    failure_reason VARCHAR(500)
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(performed_by_user_id);
CREATE INDEX idx_audit_performed_at ON audit_log(performed_at DESC);

-- ========================================================================
-- 12. WEBHOOK / INTEGRATION API
-- ========================================================================
CREATE TABLE IF NOT EXISTS webhook_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    endpoint_url VARCHAR(500) NOT NULL,
    event_types TEXT NOT NULL, -- JSON array: ["ALERT_CREATED", "INCIDENT_REPORTED"]
    enabled BOOLEAN NOT NULL DEFAULT true,
    secret_key VARCHAR(200),
    headers_json TEXT, -- JSON of custom headers
    retry_count INT NOT NULL DEFAULT 3,
    timeout_seconds INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id UUID REFERENCES webhook_config(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    payload_json TEXT,
    http_status_code INT,
    response_body TEXT,
    attempt_count INT NOT NULL DEFAULT 1,
    delivered_at TIMESTAMP,
    failed BOOLEAN NOT NULL DEFAULT false,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_delivery_webhook ON webhook_delivery_log(webhook_id);
CREATE INDEX idx_webhook_delivery_created_at ON webhook_delivery_log(created_at DESC);

-- ========================================================================
-- 13. MULTI-TENANCY SUPPORT
-- ========================================================================
CREATE TABLE IF NOT EXISTS tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE, -- short identifier
    contact_name VARCHAR(100),
    contact_email VARCHAR(100),
    contact_phone VARCHAR(20),
    subscription_plan VARCHAR(50), -- FREE, BASIC, PREMIUM, ENTERPRISE
    subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, CANCELLED
    max_users INT,
    max_devices INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT true
);

-- Add tenant_id to key tables (migration step - add columns to existing tables)
-- ALTER TABLE "user" ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenant(id);
-- ALTER TABLE zone ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenant(id);
-- ALTER TABLE device ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenant(id);
-- ALTER TABLE alert ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenant(id);

CREATE INDEX idx_tenant_code ON tenant(code);

-- ========================================================================
-- 14. ANOMALY DETECTION
-- ========================================================================
CREATE TABLE IF NOT EXISTS anomaly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anomaly_type VARCHAR(50) NOT NULL, -- SUSPICIOUS_ACCESS_PATTERN, RAPID_ACCESS, AFTER_HOURS, etc.
    severity VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50), -- USER, DEVICE, ZONE
    entity_id UUID,
    description VARCHAR(1000) NOT NULL,
    details_json TEXT, -- Additional context as JSON
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed BOOLEAN NOT NULL DEFAULT false,
    reviewed_by_user_id UUID REFERENCES "user"(id),
    reviewed_at TIMESTAMP,
    false_positive BOOLEAN NOT NULL DEFAULT false,
    action_taken VARCHAR(500)
);

CREATE INDEX idx_anomaly_type ON anomaly(anomaly_type);
CREATE INDEX idx_anomaly_detected_at ON anomaly(detected_at DESC);
CREATE INDEX idx_anomaly_reviewed ON anomaly(reviewed);

-- ========================================================================
-- 15. DEVICE HEALTH MONITORING
-- ========================================================================
CREATE TABLE IF NOT EXISTS device_health_metric (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    battery_percentage INT,
    signal_strength_dbm INT,
    firmware_version VARCHAR(50),
    temperature_celsius DECIMAL(5,2),
    uptime_hours INT,
    error_count INT DEFAULT 0,
    last_maintenance_date DATE,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_health_device ON device_health_metric(device_id);
CREATE INDEX idx_device_health_recorded_at ON device_health_metric(recorded_at DESC);

-- ========================================================================
-- 16. FACIAL RECOGNITION
-- ========================================================================
CREATE TABLE IF NOT EXISTS face_enrollment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES "user"(id),
    visitor_id UUID REFERENCES visitor(id),
    face_encoding_json TEXT NOT NULL, -- Serialized face embedding vector
    photo_url VARCHAR(500),
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS face_match_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES device(id),
    enrollment_id UUID REFERENCES face_enrollment(id),
    confidence_score DECIMAL(5,4), -- 0.0000 to 1.0000
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    access_granted BOOLEAN NOT NULL,
    photo_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS watchlist_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    reason VARCHAR(500),
    face_encoding_json TEXT NOT NULL,
    photo_url VARCHAR(500),
    alert_on_match BOOLEAN NOT NULL DEFAULT true,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_face_enrollment_user ON face_enrollment(user_id);
CREATE INDEX idx_face_match_device ON face_match_log(device_id);
CREATE INDEX idx_face_match_matched_at ON face_match_log(matched_at DESC);

-- ========================================================================
-- 17. LICENSE PLATE RECOGNITION (LPR)
-- ========================================================================
CREATE TABLE IF NOT EXISTS vehicle_registration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number VARCHAR(20) NOT NULL,
    user_id UUID REFERENCES "user"(id),
    visitor_id UUID REFERENCES visitor(id),
    vehicle_make VARCHAR(50),
    vehicle_model VARCHAR(50),
    vehicle_color VARCHAR(30),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, BLACKLISTED, EXPIRED
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lpr_detection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id UUID REFERENCES device(id),
    plate_number VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5,4),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vehicle_registration_id UUID REFERENCES vehicle_registration(id),
    entry_or_exit VARCHAR(10), -- ENTRY, EXIT
    photo_url VARCHAR(500),
    zone_id UUID REFERENCES zone(id)
);

CREATE INDEX idx_vehicle_plate ON vehicle_registration(plate_number);
CREATE INDEX idx_vehicle_user ON vehicle_registration(user_id);
CREATE INDEX idx_lpr_detection_camera ON lpr_detection(camera_id);
CREATE INDEX idx_lpr_detection_detected_at ON lpr_detection(detected_at DESC);

-- ========================================================================
-- 18. TWO-FACTOR ACCESS CONTROL
-- ========================================================================
CREATE TABLE IF NOT EXISTS two_factor_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID REFERENCES zone(id),
    device_id UUID REFERENCES device(id),
    require_badge BOOLEAN NOT NULL DEFAULT true,
    require_pin BOOLEAN NOT NULL DEFAULT false,
    require_biometric BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS two_factor_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES device(id),
    user_id UUID REFERENCES "user"(id),
    factor_1_type VARCHAR(50), -- BADGE, QR_CODE
    factor_1_verified BOOLEAN,
    factor_2_type VARCHAR(50), -- PIN, BIOMETRIC
    factor_2_verified BOOLEAN,
    access_granted BOOLEAN NOT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_2fa_policy_zone ON two_factor_policy(zone_id);
CREATE INDEX idx_2fa_log_device ON two_factor_access_log(device_id);

-- ========================================================================
-- 19. ROLE-BASED ZONE ACCESS POLICIES
-- ========================================================================
CREATE TABLE IF NOT EXISTS zone_access_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID NOT NULL REFERENCES zone(id),
    role VARCHAR(50) NOT NULL, -- ADMIN, SECURITY_OFFICER, VIEWER, CUSTOM_ROLE
    allowed_days INT[], -- Array: [1,2,3,4,5] = Mon-Fri
    start_time TIME,
    end_time TIME,
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    approver_role VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS temporary_access_grant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id),
    zone_id UUID NOT NULL REFERENCES zone(id),
    granted_by_user_id UUID REFERENCES "user"(id),
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    reason VARCHAR(500),
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_zone_policy_zone ON zone_access_policy(zone_id);
CREATE INDEX idx_temp_access_user ON temporary_access_grant(user_id);
CREATE INDEX idx_temp_access_zone ON temporary_access_grant(zone_id);

-- ========================================================================
-- 20. CONTRACTOR/TEMPORARY WORKER MANAGEMENT
-- ========================================================================
CREATE TABLE IF NOT EXISTS contractor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone_number VARCHAR(20) NOT NULL,
    company VARCHAR(100),
    contractor_type VARCHAR(50), -- MAINTENANCE, CLEANING, DELIVERY, CONSTRUCTION, IT
    sponsor_user_id UUID REFERENCES "user"(id),
    background_check_status VARCHAR(50), -- PENDING, PASSED, FAILED, NOT_REQUIRED
    background_check_date DATE,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, REVOKED
    allowed_zones TEXT, -- JSON array of zone IDs
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(1000)
);

CREATE INDEX idx_contractor_sponsor ON contractor(sponsor_user_id);
CREATE INDEX idx_contractor_status ON contractor(status);
CREATE INDEX idx_contractor_valid_until ON contractor(valid_until);

-- ========================================================================
-- 21. ASSET TRACKING
-- ========================================================================
CREATE TABLE IF NOT EXISTS asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_tag VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    asset_type VARCHAR(50), -- LAPTOP, PROJECTOR, EQUIPMENT, VEHICLE, etc.
    rfid_tag VARCHAR(100),
    ble_beacon_id VARCHAR(100),
    assigned_to_user_id UUID REFERENCES "user"(id),
    home_zone_id UUID REFERENCES zone(id),
    current_zone_id UUID REFERENCES zone(id),
    value_usd DECIMAL(12,2),
    purchase_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_SERVICE', -- IN_SERVICE, MAINTENANCE, MISSING, RETIRED
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS asset_movement_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES asset(id) ON DELETE CASCADE,
    from_zone_id UUID REFERENCES zone(id),
    to_zone_id UUID REFERENCES zone(id),
    device_id UUID REFERENCES device(id), -- Access point that detected movement
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    movement_type VARCHAR(50) -- ENTRY, EXIT, DETECTED
);

CREATE INDEX idx_asset_tag ON asset(asset_tag);
CREATE INDEX idx_asset_current_zone ON asset(current_zone_id);
CREATE INDEX idx_asset_movement_asset ON asset_movement_log(asset_id);
CREATE INDEX idx_asset_movement_detected_at ON asset_movement_log(detected_at DESC);

-- ========================================================================
-- 22. ELEVATOR INTEGRATION
-- ========================================================================
CREATE TABLE IF NOT EXISTS elevator (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    building VARCHAR(100),
    device_id UUID REFERENCES device(id), -- Badge reader device
    max_floor INT NOT NULL,
    min_floor INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS elevator_access_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    elevator_id UUID NOT NULL REFERENCES elevator(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    allowed_floors INT[] NOT NULL, -- Array of floor numbers
    time_restricted BOOLEAN NOT NULL DEFAULT false,
    start_time TIME,
    end_time TIME,
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS elevator_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    elevator_id UUID NOT NULL REFERENCES elevator(id),
    user_id UUID REFERENCES "user"(id),
    requested_floor INT NOT NULL,
    access_granted BOOLEAN NOT NULL,
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    denial_reason VARCHAR(200)
);

CREATE INDEX idx_elevator_device ON elevator(device_id);
CREATE INDEX idx_elevator_policy_elevator ON elevator_access_policy(elevator_id);
CREATE INDEX idx_elevator_log_elevator ON elevator_access_log(elevator_id);
CREATE INDEX idx_elevator_log_accessed_at ON elevator_access_log(accessed_at DESC);

-- ========================================================================
-- END OF MIGRATION
-- ========================================================================
