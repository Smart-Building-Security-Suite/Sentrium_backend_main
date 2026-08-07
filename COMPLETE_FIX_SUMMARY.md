# Complete Fix: Device Creation + All Tables

## The Bug

When creating a device (or potentially other entities), you get:
```
null value in column "id" of relation "device" violates not-null constraint
```

## Root Cause

The database tables are missing `DEFAULT gen_random_uuid()` on their `id` columns. Hibernate's `GenerationType.UUID` expects the database to have this DEFAULT value, otherwise it sends `null` for the ID.

## The Complete Fix

Run this in your Neon SQL Editor (https://console.neon.tech):

### Option 1: Just Fix Device Table (Quick Fix)

```sql
ALTER TABLE device 
ALTER COLUMN id SET DEFAULT gen_random_uuid();
```

This fixes device creation immediately.

### Option 2: Fix All Tables (Recommended - Prevent Future Issues)

Copy and run the entire `FIX_ALL_UUID_DEFAULTS.sql` file in Neon SQL Editor.

This fixes **ALL** tables at once:
- ✅ device
- ✅ user
- ✅ zone
- ✅ alert
- ✅ alert_rule
- ✅ access_rule
- ✅ access_log
- ✅ anomaly
- ✅ incident
- ✅ notification
- ✅ motion_event
- ✅ visitor
- ✅ emergency_contact
- ✅ emergency_event
- ✅ report
- ✅ device_status_history
- ✅ patrol_route
- ✅ patrol_checkpoint
- ✅ patrol_session
- ✅ patrol_checkpoint_scan
- ✅ mobile_access_token
- ✅ push_notification_device
- ✅ analytics_daily
- ✅ otp_record
- ✅ pending_signup
- ✅ revoked_token
- ✅ device_command
- ✅ video_clip
- ✅ recording_schedule
- ✅ discovered_camera

## Why This Happens

When using Hibernate with `GenerationType.UUID`:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

PostgreSQL tables MUST have:
```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

Without the `DEFAULT`, Hibernate sends `null` and PostgreSQL rejects it.

## Tables Already Fixed

The new tables created by `DEVICE_INTEGRATION_MIGRATION.sql` already have the DEFAULT:
- ✅ device_command
- ✅ video_clip
- ✅ recording_schedule
- ✅ discovered_camera

## Tables Likely Missing DEFAULT

Most of the old tables created before the migration:
- ❌ device (CONFIRMED)
- ❌ user
- ❌ zone
- ❌ alert
- ❌ And ~25 others...

## How to Apply

### Step 1: Run the Fix

Go to https://console.neon.tech:
1. Select your project
2. Click "SQL Editor"
3. Copy contents of `FIX_ALL_UUID_DEFAULTS.sql`
4. Paste and click "Run"

Expected output:
```
ALTER TABLE (30+ times)
```

### Step 2: Verify

The script includes verification queries. You should see all tables with:
```
column_default: 'gen_random_uuid()'
```

### Step 3: Test Device Creation

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

## Why Some Tables Work

You mentioned auth and zones work. They might:
1. Already have the DEFAULT (if created with a better script)
2. Have data that was manually inserted with explicit UUIDs
3. Been created via `ddl-auto=create` which adds the DEFAULT automatically

But `device` was likely created manually or via a script missing the DEFAULT.

## Prevention for Future Tables

Always create UUID primary keys as:
```sql
CREATE TABLE my_table (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- other columns...
);
```

Never just:
```sql
CREATE TABLE my_table (
    id UUID PRIMARY KEY,  -- WRONG! Missing DEFAULT
    -- other columns...
);
```

## Summary

**Just Device**: Run Option 1 (1 line)  
**All Tables**: Run `FIX_ALL_UUID_DEFAULTS.sql` (prevents future issues)

Both fix the bug. Option 2 is safer for the long term.
