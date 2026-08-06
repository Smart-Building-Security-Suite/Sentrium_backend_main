-- ========================================================================
-- DEVICE INTEGRATION & REAL CAMERA SUPPORT MIGRATION
-- Adds fields for physical device connectivity and camera streams
-- ========================================================================

-- ========================================================================
-- 1. DEVICE CONNECTIVITY FIELDS
-- Add fields for HTTP/MQTT device communication
-- ========================================================================

ALTER TABLE device ADD COLUMN IF NOT EXISTS endpoint_url VARCHAR(500);
ALTER TABLE device ADD COLUMN IF NOT EXISTS api_key_encrypted VARCHAR(500);
ALTER TABLE device ADD COLUMN IF NOT EXISTS connection_protocol VARCHAR(20) DEFAULT 'HTTP';
ALTER TABLE device ADD COLUMN IF NOT EXISTS connection_status VARCHAR(20) DEFAULT 'DISCONNECTED';
ALTER TABLE device ADD COLUMN IF NOT EXISTS last_command_at TIMESTAMP;
ALTER TABLE device ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(50);

-- ========================================================================
-- 2. CAMERA STREAM CONFIGURATION
-- Add fields for RTSP/HTTP stream URLs
-- ========================================================================

ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_url VARCHAR(500);
ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_type VARCHAR(20);
ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_username VARCHAR(100);
ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_password_encrypted VARCHAR(500);
ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_resolution VARCHAR(20);
ALTER TABLE device ADD COLUMN IF NOT EXISTS stream_fps INT;

-- ========================================================================
-- 3. DEVICE COMMANDS LOG
-- Track all commands sent to devices
-- ========================================================================

CREATE TABLE IF NOT EXISTS device_command (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    command_type VARCHAR(50) NOT NULL, -- UNLOCK, LOCK, REBOOT, etc.
    command_payload TEXT, -- JSON payload
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED, TIMEOUT
    requested_by_user_id UUID REFERENCES "user"(id),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    response_payload TEXT, -- Device response
    error_message VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_device_command_device ON device_command(device_id);
CREATE INDEX IF NOT EXISTS idx_device_command_status ON device_command(status);
CREATE INDEX IF NOT EXISTS idx_device_command_requested_at ON device_command(requested_at DESC);

-- ========================================================================
-- 4. VIDEO CLIPS (Enhanced from existing schema)
-- Store recorded video segments
-- ========================================================================

CREATE TABLE IF NOT EXISTS video_clip (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id UUID REFERENCES device(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    duration_seconds INT,
    file_url VARCHAR(500),
    file_path VARCHAR(500), -- Local filesystem path if stored locally
    file_size_bytes BIGINT,
    resolution VARCHAR(20),
    format VARCHAR(10) DEFAULT 'MP4', -- MP4, MKV, etc.
    trigger_type VARCHAR(50), -- MOTION, ALERT, MANUAL, SCHEDULED
    trigger_event_id UUID, -- Reference to motion_event or alert
    thumbnail_url VARCHAR(500),
    thumbnail_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_until TIMESTAMP,
    archived BOOLEAN NOT NULL DEFAULT false,
    archived_at TIMESTAMP,
    metadata JSONB -- Additional metadata (codec, bitrate, etc.)
);

CREATE INDEX IF NOT EXISTS idx_video_clip_camera ON video_clip(camera_id);
CREATE INDEX IF NOT EXISTS idx_video_clip_start_time ON video_clip(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_video_clip_trigger_type ON video_clip(trigger_type);
CREATE INDEX IF NOT EXISTS idx_video_clip_retention ON video_clip(retention_until) WHERE NOT archived;

-- ========================================================================
-- 5. RECORDING SCHEDULES
-- Define when cameras should record
-- ========================================================================

CREATE TABLE IF NOT EXISTS recording_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    camera_id UUID NOT NULL REFERENCES device(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    recording_mode VARCHAR(20) NOT NULL DEFAULT 'MOTION', -- CONTINUOUS, MOTION, SCHEDULED
    days_of_week INT[], -- [1,2,3,4,5] = Mon-Fri, NULL = all days
    start_time TIME,
    end_time TIME,
    pre_record_seconds INT DEFAULT 5, -- Record N seconds before motion
    post_record_seconds INT DEFAULT 10, -- Record N seconds after motion
    retention_days INT DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recording_schedule_camera ON recording_schedule(camera_id);
CREATE INDEX IF NOT EXISTS idx_recording_schedule_enabled ON recording_schedule(enabled);

-- ========================================================================
-- 6. CAMERA DISCOVERY CACHE
-- Store discovered cameras from network scans
-- ========================================================================

CREATE TABLE IF NOT EXISTS discovered_camera (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address VARCHAR(45) NOT NULL,
    mac_address VARCHAR(17),
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    firmware_version VARCHAR(50),
    stream_url VARCHAR(500),
    onvif_supported BOOLEAN DEFAULT false,
    discovered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed BOOLEAN DEFAULT false, -- True if added to devices table
    device_id UUID REFERENCES device(id)
);

CREATE INDEX IF NOT EXISTS idx_discovered_camera_ip ON discovered_camera(ip_address);
CREATE INDEX IF NOT EXISTS idx_discovered_camera_claimed ON discovered_camera(claimed);
CREATE INDEX IF NOT EXISTS idx_discovered_camera_discovered_at ON discovered_camera(discovered_at DESC);

-- ========================================================================
-- 7. UPDATE EXISTING MOTION_EVENT TO LINK TO VIDEO CLIPS
-- ========================================================================

ALTER TABLE motion_event ADD COLUMN IF NOT EXISTS video_clip_id UUID REFERENCES video_clip(id);
CREATE INDEX IF NOT EXISTS idx_motion_event_video_clip ON motion_event(video_clip_id);

-- ========================================================================
-- END OF MIGRATION
-- ========================================================================

-- Sample data for testing (optional)
-- COMMENT OUT IF NOT NEEDED

-- Add connection protocol enum values
COMMENT ON COLUMN device.connection_protocol IS 'HTTP, MQTT, WEBSOCKET, or NONE';
COMMENT ON COLUMN device.connection_status IS 'CONNECTED, DISCONNECTED, ERROR';
COMMENT ON COLUMN device.stream_type IS 'RTSP, HTTP, HLS, or NULL for non-cameras';
