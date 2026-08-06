-- ========================================================================
-- EXPO PUSH NOTIFICATION SYSTEM - DATABASE MIGRATION
-- ========================================================================

CREATE TABLE IF NOT EXISTS push_notification_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expo_token VARCHAR(500) NOT NULL UNIQUE,
    device_type VARCHAR(20) NOT NULL, -- IOS, ANDROID
    device_name VARCHAR(100),
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_push_device_user ON push_notification_device(user_id);
CREATE INDEX idx_push_device_token ON push_notification_device(expo_token);
CREATE INDEX idx_push_device_active ON push_notification_device(active);

-- ========================================================================
-- EXPO PUSH NOTIFICATION SYSTEM READY
-- ========================================================================
