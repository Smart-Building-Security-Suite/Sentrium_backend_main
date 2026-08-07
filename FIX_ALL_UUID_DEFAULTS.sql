-- ========================================================================
-- FIX ALL UUID PRIMARY KEY COLUMNS TO HAVE DEFAULT gen_random_uuid()
-- This fixes the "null value in column id violates not-null constraint" error
-- ========================================================================

-- First, let's check which tables are missing the DEFAULT
SELECT
    table_name,
    column_name,
    data_type,
    column_default
FROM information_schema.columns
WHERE
    table_schema = 'public'
    AND column_name = 'id'
    AND data_type = 'uuid'
    AND (column_default IS NULL OR column_default NOT LIKE '%gen_random_uuid%')
ORDER BY table_name;

-- ========================================================================
-- CORE TABLES (likely created before the migration)
-- ========================================================================

-- Fix device table (CONFIRMED ISSUE)
ALTER TABLE device
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix user table
ALTER TABLE "user"
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix zone table
ALTER TABLE zone
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix alert table
ALTER TABLE alert
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix alert_rule table
ALTER TABLE alert_rule
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix access_rule table
ALTER TABLE access_rule
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix access_log table
ALTER TABLE access_log
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix anomaly table
ALTER TABLE anomaly
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix incident table
ALTER TABLE incident
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix notification table
ALTER TABLE notification
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix motion_event table
ALTER TABLE motion_event
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix visitor table
ALTER TABLE visitor
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix emergency_contact table
ALTER TABLE emergency_contact
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix emergency_event table
ALTER TABLE emergency_event
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix report table
ALTER TABLE report
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix device_status_history table
ALTER TABLE device_status_history
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix patrol tables
ALTER TABLE patrol_route
ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE patrol_checkpoint
ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE patrol_session
ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE patrol_checkpoint_scan
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix mobile access token table
ALTER TABLE mobile_access_token
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix push notification device table
ALTER TABLE push_notification_device
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix analytics table
ALTER TABLE analytics_daily
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ========================================================================
-- AUTH TABLES
-- ========================================================================

-- Fix otp_record table
ALTER TABLE otp_record
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix pending_signup table
ALTER TABLE pending_signup
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Fix revoked_token table
ALTER TABLE revoked_token
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ========================================================================
-- NEW TABLES FROM DEVICE INTEGRATION MIGRATION
-- These should already have DEFAULT, but let's ensure it
-- ========================================================================

-- device_command (should already be correct from migration)
ALTER TABLE device_command
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- video_clip (should already be correct from migration)
ALTER TABLE video_clip
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- recording_schedule (should already be correct from migration)
ALTER TABLE recording_schedule
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- discovered_camera (should already be correct from migration)
ALTER TABLE discovered_camera
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ========================================================================
-- VERIFICATION
-- Check all tables now have the DEFAULT
-- ========================================================================

SELECT
    table_name,
    column_name,
    data_type,
    column_default
FROM information_schema.columns
WHERE
    table_schema = 'public'
    AND column_name = 'id'
    AND data_type = 'uuid'
ORDER BY table_name;

-- Should see gen_random_uuid() for all UUID id columns now!
