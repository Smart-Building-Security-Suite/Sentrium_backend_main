# Solution: Device ID Null Constraint Violation

## The Problem

When creating a device, you get this error:
```
null value in column "id" of relation "device" violates not-null constraint
```

The INSERT statement shows:
```sql
insert into "device" ("id", ...) 
overriding system value 
values (null, ...)
```

This means Hibernate is sending `null` for the ID instead of letting the database generate it.

## Root Cause

The `device` table is missing a DEFAULT value for the `id` column. Other tables (zone, user, alert) likely have this:

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

But the `device` table probably has:
```sql
id UUID PRIMARY KEY  -- Missing DEFAULT!
```

## Fix Option 1: Add Database Default (RECOMMENDED)

Run this in Neon SQL Editor:

```sql
ALTER TABLE device 
ALTER COLUMN id SET DEFAULT gen_random_uuid();
```

Then restart your backend.

## Fix Option 2: Check Table Creation

The device table might have been created differently than other tables. Check if the migration script was run completely:

1. Go to Neon SQL Editor: https://console.neon.tech
2. Run this to check:

```sql
-- Check the id column default
SELECT column_name, column_default, data_type
FROM information_schema.columns
WHERE table_name = 'device' AND column_name = 'id';

-- Compare with zone table (which works)
SELECT column_name, column_default, data_type
FROM information_schema.columns
WHERE table_name = 'zone' AND column_name = 'id';
```

If `device.id` has `column_default = NULL` but `zone.id` has `column_default = 'gen_random_uuid()'`, that's the issue!

## Fix Option 3: Recreate Device Table (Nuclear Option)

⚠️ **WARNING: This deletes all devices!**

```sql
-- Backup first!
CREATE TABLE device_backup AS SELECT * FROM device;

-- Drop and recreate
DROP TABLE IF EXISTS device CASCADE;

CREATE TABLE device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    zone_id UUID NOT NULL REFERENCES zone(id),
    active BOOLEAN NOT NULL DEFAULT true,
    deactivated_at TIMESTAMP,
    last_heartbeat_at TIMESTAMP,
    
    -- Connectivity fields
    endpoint_url VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    connection_protocol VARCHAR(20) DEFAULT 'HTTP',
    connection_status VARCHAR(20) DEFAULT 'DISCONNECTED',
    last_command_at TIMESTAMP,
    firmware_version VARCHAR(50),
    
    -- Camera stream fields
    stream_url VARCHAR(500),
    stream_type VARCHAR(20),
    stream_username VARCHAR(100),
    stream_password_encrypted VARCHAR(500),
    stream_resolution VARCHAR(20),
    stream_fps INT
);
```

## Why This Happened

When `DEVICE_INTEGRATION_MIGRATION.sql` ran with `ALTER TABLE device ADD COLUMN ...`, it added the new columns but didn't fix the existing `id` column if it was created incorrectly.

The original device table creation might have been missing `DEFAULT gen_random_uuid()`.

## Quick Test

After applying Fix Option 1, test device creation:

```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Camera",
    "type": "CAMERA",
    "zoneId": "YOUR_ZONE_UUID"
  }'
```

Should return `201 Created` ✅

## Prevention

All UUID primary keys should be created with:
```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

Not just:
```sql
id UUID PRIMARY KEY
```

The DEFAULT clause is critical for Hibernate's `GenerationType.UUID` to work correctly with PostgreSQL.
