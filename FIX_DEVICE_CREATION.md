# Fix: Device Creation 500 Error

## Root Cause Found ✅

The `Device` entity was updated with new fields (connectivity & camera stream fields) but **the database migration was NOT applied**.

### Evidence:
1. ✅ Auth endpoints work → Database is connected
2. ✅ Zone endpoints work → Basic tables exist
3. ❌ Device creation fails → Missing new columns added in commit `b73e24b`

### What's Missing:
The `device` table is missing these columns:
- `endpoint_url`
- `api_key_encrypted`
- `connection_protocol`
- `connection_status`
- `last_command_at`
- `firmware_version`
- `stream_url`
- `stream_type`
- `stream_username`
- `stream_password_encrypted`
- `stream_resolution`
- `stream_fps`

## Solution (2 minutes)

### Option 1: Run the Migration (RECOMMENDED)

```bash
# 1. Navigate to project directory
cd /Users/qoretex/Desktop/Sentrium_backend_main

# 2. Connect to your database and apply migration
# Replace connection details if different
psql -h localhost -U postgres -d security_suite_dev -f DEVICE_INTEGRATION_MIGRATION.sql

# 3. Restart your backend server
```

### Option 2: If psql command not found

If you're using Docker Postgres:
```bash
docker exec -i <container-name> psql -U postgres -d security_suite_dev < DEVICE_INTEGRATION_MIGRATION.sql
```

Find your container name:
```bash
docker ps | grep postgres
```

### Option 3: Quick Dev Fix (NOT for production)

Temporarily change `application.yml`:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Change from 'none' to 'update'
```

Then restart the backend. Hibernate will auto-add missing columns.

⚠️ **WARNING**: Change it back to `none` or `validate` after testing!

## Verify the Fix

1. **Check the columns were added:**
```bash
psql -h localhost -U postgres -d security_suite_dev -c "\d device"
```

Look for the new columns in the output.

2. **Test device creation:**
Use your frontend or curl:
```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Camera",
    "type": "CAMERA",
    "zoneId": "your-zone-uuid-here"
  }'
```

Should return `201 Created`.

## Why This Happened

1. Code was updated in commit `b73e24b` (feat: add device integration & video clips)
2. New fields were added to the `Device` JPA entity
3. Migration SQL file was created: `DEVICE_INTEGRATION_MIGRATION.sql`
4. **BUT** the migration was never executed against the database
5. Result: Entity expects columns that don't exist → SQL error → 500 response

## Prevention

For future updates:
- Always run migration scripts before deploying new backend code
- Consider using Flyway or Liquibase for automatic migrations
- Set `ddl-auto: validate` in production to catch schema mismatches early
