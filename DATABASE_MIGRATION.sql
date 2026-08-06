-- Database Migration Script for Backend Improvements
-- Run this script to add new columns and tables for the enhanced features

-- 1. Add soft-delete fields to Device table
ALTER TABLE device
ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP;

-- 2. Create DeviceStatusHistory table for device timeline tracking
CREATE TABLE IF NOT EXISTS device_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500),
    CONSTRAINT fk_device_history FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_device_status_history_device_id ON device_status_history(device_id);
CREATE INDEX IF NOT EXISTS idx_device_status_history_recorded_at ON device_status_history(recorded_at DESC);

-- Note: No migration needed for WebSocket, rate limit headers, or new endpoints
-- as they don't require schema changes
