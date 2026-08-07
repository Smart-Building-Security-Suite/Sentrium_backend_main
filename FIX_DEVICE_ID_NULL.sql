-- Fix: Device ID null value constraint violation
-- This adds a default UUID generator to the id column

-- Check current table structure
SELECT
    column_name,
    data_type,
    column_default,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'device'
  AND column_name IN ('id', 'name', 'type', 'zone_id', 'status', 'active');

-- Add default UUID generation for id column if not present
ALTER TABLE device
ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Verify the fix
SELECT
    column_name,
    column_default
FROM information_schema.columns
WHERE table_name = 'device'
  AND column_name = 'id';
