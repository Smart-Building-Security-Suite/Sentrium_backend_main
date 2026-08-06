# Troubleshooting: 500 Error on POST /devices

## Problem

```json
{
  "backendMessage": "An unexpected error occurred",
  "method": "post",
  "status": 500,
  "url": "/devices"
}
```

## Root Cause

The database migration `DEVICE_INTEGRATION_MIGRATION.sql` has **NOT been run yet**.

The new device connectivity fields (endpoint_url, connection_protocol, stream_url, etc.) don't exist in the database, causing the INSERT to fail.

---

## ✅ Solution: Run the Migration

### Step 1: Check if Migration is Needed

```bash
# Connect to database
psql -d security_suite_dev -U postgres

# Check if new columns exist
\d device

# If you DON'T see these columns, you need to run the migration:
# - endpoint_url
# - connection_protocol
# - stream_url
```

### Step 2: Run the Migration

```bash
# Option A: Via psql command
psql -d security_suite_dev -U postgres -f DEVICE_INTEGRATION_MIGRATION.sql

# Option B: Copy-paste SQL into psql
psql -d security_suite_dev -U postgres
\i /path/to/DEVICE_INTEGRATION_MIGRATION.sql

# Option C: Production (use your DB credentials)
psql -h your-db-host -d your-db-name -U your-user -f DEVICE_INTEGRATION_MIGRATION.sql
```

### Step 3: Verify Migration

```sql
-- In psql
\d device

-- You should see:
-- endpoint_url              | character varying(500)
-- api_key_encrypted         | character varying(500)
-- connection_protocol       | character varying(20) | default 'HTTP'
-- connection_status         | character varying(20) | default 'DISCONNECTED'
-- stream_url                | character varying(500)
-- stream_type               | character varying(20)
-- ... etc
```

### Step 4: Restart Backend

```bash
# Stop current instance
Ctrl+C

# Restart
./mvnw spring-boot:run
```

### Step 5: Test Again

```bash
# Create device
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Door",
    "type": "ACCESS_POINT",
    "zoneId": "your-zone-uuid"
  }'

# Should return 201 Created with device details
```

---

## 🔍 Alternative: Check Backend Logs

If the error persists after migration, check the backend logs:

```bash
# While backend is running, look for:
# - SQLException
# - "column does not exist"
# - JPA/Hibernate errors

# Example error:
# ERROR: column "connection_protocol" of relation "device" does not exist
```

---

## 🚨 If Using Docker/Render/Heroku

### Docker Compose

```yaml
# Add migration step to docker-compose.yml
services:
  db:
    image: postgres:15
    ...
  
  backend:
    depends_on:
      - db
    command: >
      sh -c "
        psql $DATABASE_URL -f /app/DEVICE_INTEGRATION_MIGRATION.sql &&
        java -jar /app/backend.jar
      "
```

### Render

1. Go to Render Dashboard → Your Database
2. Click "Connect" → Copy connection string
3. Run locally:
   ```bash
   psql "your-render-connection-string" -f DEVICE_INTEGRATION_MIGRATION.sql
   ```
4. Restart your backend service on Render

### Heroku

```bash
# Run migration on Heroku Postgres
heroku pg:psql --app your-app-name < DEVICE_INTEGRATION_MIGRATION.sql
```

---

## 🧪 Quick Test Without Migration

If you want to test the API **without running the full migration**, you can temporarily remove the new fields:

**NOT RECOMMENDED** - But useful for debugging:

### Option 1: Remove Field Defaults (Temporary)

Edit `Device.java` and remove the `= "HTTP"` and `= "DISCONNECTED"` defaults:

```java
// Change this:
private String connectionProtocol = "HTTP";
private String connectionStatus = "DISCONNECTED";

// To this:
private String connectionProtocol; // Will be null
private String connectionStatus;   // Will be null
```

Then restart backend. Device creation will work but connectivity features won't.

### Option 2: Use Old Version

```bash
# Revert to before connectivity features
git log --oneline
git checkout <commit-before-device-integration>
./mvnw spring-boot:run
```

---

## ✅ Recommended: Production Deployment Checklist

Before deploying to production:

1. **Backup database**
   ```bash
   pg_dump -U postgres -d security_suite_dev > backup_$(date +%Y%m%d).sql
   ```

2. **Run migration on staging first**
   ```bash
   psql -d security_suite_staging -f DEVICE_INTEGRATION_MIGRATION.sql
   ```

3. **Test all endpoints on staging**
   - POST /devices (create)
   - GET /devices (list)
   - POST /devices/{id}/configure
   - POST /devices/{id}/unlock

4. **Run migration on production**
   ```bash
   psql -d security_suite_prod -f DEVICE_INTEGRATION_MIGRATION.sql
   ```

5. **Deploy backend**

6. **Verify production**
   ```bash
   curl https://your-backend.com/api/v1/devices \
     -H "Authorization: Bearer $TOKEN"
   ```

---

## 📋 Migration Includes

The `DEVICE_INTEGRATION_MIGRATION.sql` file adds:

**New Columns to `device` table:**
- endpoint_url (device HTTP endpoint)
- api_key_encrypted (device authentication)
- connection_protocol (HTTP/MQTT/WEBSOCKET)
- connection_status (CONNECTED/DISCONNECTED/ERROR)
- stream_url (RTSP camera URL)
- stream_type (RTSP/HTTP/HLS)
- stream_username (camera auth)
- stream_password_encrypted (camera auth)

**New Tables:**
- device_command (command history)
- video_clip (recorded videos)
- recording_schedule (future use)
- discovered_camera (future use)

**All with proper indexes and constraints.**

---

## 🐛 Still Getting 500 Error?

### Check Actual Error Message

```bash
# View full error in backend logs
tail -f logs/spring-boot-application.log

# Or if running in terminal:
# Look for full stack trace after the error
```

### Common Issues

1. **Zone doesn't exist**
   ```
   Error: Zone not found
   Fix: Create a zone first via POST /zones
   ```

2. **Invalid DeviceType**
   ```
   Error: Invalid value for DeviceType
   Fix: Use ACCESS_POINT, CAMERA_SIM, or SENSOR
   ```

3. **Database connection issue**
   ```
   Error: Connection refused
   Fix: Check DATABASE_URL in application.yml
   ```

4. **Permission denied**
   ```
   Error: permission denied for relation device
   Fix: Grant privileges: GRANT ALL ON ALL TABLES IN SCHEMA public TO your_user;
   ```

---

## 💡 Prevention

To avoid this in future deployments, add migration check to startup:

### Option A: Flyway (Recommended)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

Move SQL to: `src/main/resources/db/migration/V2__device_integration.sql`

Flyway will auto-run on startup.

### Option B: Liquibase

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

### Option C: JPA Auto-DDL (Dev Only)

```yaml
# application.yml - ONLY for development!
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Creates missing columns automatically
```

⚠️ **Never use `ddl-auto: update` in production!**

---

## 📞 Need Help?

1. Check backend logs for full stack trace
2. Verify migration was applied: `\d device` in psql
3. Check if device_command table exists: `\dt` in psql
4. Try creating a zone first: `POST /zones`
5. Check GitHub issues if using Render/Heroku

---

**TL;DR:** Run `DEVICE_INTEGRATION_MIGRATION.sql` on your database, restart backend, try again.
