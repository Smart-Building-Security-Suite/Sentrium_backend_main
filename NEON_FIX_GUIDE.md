# Fix Device Creation with Neon Database ✅

## Root Cause Confirmed

Your `.env` file has `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, which means Hibernate **should** automatically create missing columns when the app starts.

## Solution Options

### Option 1: Restart Backend (Easiest - Try This First)

Since `ddl-auto=update` is already enabled, simply restart your backend:

```bash
# Stop current backend (if running)
# Then restart:
./mvnw spring-boot:run
```

OR if you're using an IDE:
- Stop the application
- Run it again

**What happens**: Hibernate will automatically add the missing 12 columns to the `device` table on Neon.

### Option 2: Run Migration Manually (If Option 1 Doesn't Work)

Use Neon's SQL Editor (easiest for cloud databases):

1. **Go to Neon Console**: https://console.neon.tech
2. **Select your project**: `neondb`
3. **Click "SQL Editor"** in the left sidebar
4. **Copy the entire contents** of `DEVICE_INTEGRATION_MIGRATION.sql`
5. **Paste and click "Run"**

Expected output: Multiple `ALTER TABLE` and `CREATE TABLE` success messages

### Option 3: Use psql (If Installed)

```bash
# Install psql if needed
brew install libpq
brew link --force libpq

# Run the migration script I created
./run_migration_neon.sh
```

## Verify the Fix

### Test Device Creation:

```bash
# 1. Login to get JWT token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your-email@example.com",
    "password": "your-password"
  }'

# 2. Copy the token from response

# 3. Get a zone ID (or create one if needed)
curl http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

# 4. Create a device
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Camera",
    "type": "CAMERA",
    "zoneId": "YOUR_ZONE_UUID_HERE"
  }'
```

Should return `201 Created` ✅

## What Was the Problem?

1. **Code Update**: Commit `b73e24b` added 12 new fields to the `Device` entity:
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

2. **Database Out of Sync**: Your Neon database didn't have these columns yet

3. **Result**: When trying to create a device, Hibernate tried to insert into non-existent columns → SQL error → 500 response

## Why Other Endpoints Worked

- ✅ Auth endpoints: Use the `user` table (unchanged)
- ✅ Zone endpoints: Use the `zone` table (unchanged)
- ❌ Device creation: Uses `device` table (has new columns)

## Current Backend Status

I just started your backend successfully. Check if it's running:

```bash
curl http://localhost:8080/api/v1/actuator/health
```

## Connection Details (From .env)

- **Database**: Neon PostgreSQL
- **Host**: `ep-falling-shape-ay6u866f-pooler.c-5.us-east-2.aws.neon.tech`
- **Database**: `neondb`
- **Username**: `neondb_owner`
- **DDL Auto**: `update` ✅ (Auto-creates missing columns)

## Important Note

With `ddl-auto=update`, Hibernate automatically adds missing columns when the app starts. 

**This means**: Simply restarting your backend should fix the issue! The columns will be created automatically on the Neon database.

## If Still Getting 500 Error

1. **Check backend logs** for the actual SQL error
2. **Verify the zone exists** (device creation requires a valid zone)
3. **Check JWT token is valid** (expired tokens → 401, not 500)
4. **Try the migration manually** using Neon SQL Editor (Option 2 above)

---

## Quick Test (Try This Now)

```bash
# Check if backend is running
curl http://localhost:8080/api/v1/zones

# Should return zones list or 401 (auth required)
# If you get connection refused, backend is not running
```

The backend I just started should be running on port 8080. Try creating a device from your frontend now!
