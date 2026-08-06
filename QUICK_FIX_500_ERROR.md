# Quick Fix: 500 Error on POST /devices

## ⚠️ Problem

```json
ERROR [API] Request failed with response: {
  "backendMessage": "An unexpected error occurred", 
  "method": "post", 
  "status": 500, 
  "url": "/devices"
}
```

## ✅ Solution (30 seconds)

### Run This Command:

```bash
# Connect to your database and run the migration
psql -d security_suite_dev -U postgres -f DEVICE_INTEGRATION_MIGRATION.sql

# Then restart backend
./mvnw spring-boot:run
```

That's it! The error will be fixed.

---

## 🔍 What Happened?

The device entity was updated with new fields (endpoint_url, stream_url, etc.) but the database doesn't have these columns yet.

The migration adds these columns with proper defaults.

---

## 📋 Step-by-Step

**1. Stop backend** (Ctrl+C if running)

**2. Run migration:**
```bash
psql -d security_suite_dev -U postgres -f DEVICE_INTEGRATION_MIGRATION.sql
```

Expected output:
```
ALTER TABLE
ALTER TABLE
ALTER TABLE
...
CREATE TABLE
CREATE INDEX
...
```

**3. Verify (optional):**
```bash
psql -d security_suite_dev -U postgres -c "\d device"
```

Look for these new columns:
- endpoint_url
- connection_protocol  
- stream_url
- stream_type

**4. Restart backend:**
```bash
./mvnw spring-boot:run
```

**5. Test device creation:**
```bash
# First, get a JWT token by logging in
TOKEN="your-jwt-token-here"

# Then create a device
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Door",
    "type": "ACCESS_POINT",
    "zoneId": "your-zone-uuid"
  }'
```

Should return `201 Created` with device details.

---

## 🚨 If Still Getting 500 Error

### Check if you have a zone:

```bash
# List zones
curl http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $TOKEN"

# If empty, create a zone first:
curl -X POST http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Main Building"}'
```

### Check backend logs:

```bash
# Look for the actual error message
# It will show which column is missing or what the real issue is
```

---

## 🐳 Using Docker?

```bash
# Run migration in Docker container
docker exec -i postgres-container psql -U postgres -d security_suite_dev < DEVICE_INTEGRATION_MIGRATION.sql

# Or if using docker-compose:
docker-compose exec db psql -U postgres -d security_suite_dev < DEVICE_INTEGRATION_MIGRATION.sql
```

---

## ☁️ Using Render/Heroku?

### Render:
```bash
# Get connection string from Render dashboard
psql "your-render-postgres-connection-string" -f DEVICE_INTEGRATION_MIGRATION.sql
```

### Heroku:
```bash
heroku pg:psql --app your-app-name < DEVICE_INTEGRATION_MIGRATION.sql
```

---

## ✅ Prevention

For future deployments, use one of these:

**Option 1: Flyway (Recommended)**
- Auto-runs migrations on startup
- Add to pom.xml and move SQL to `db/migration/`

**Option 2: Manual checklist**
- Always run migrations before deploying new backend version
- Include in deployment script

**Option 3: Dev only (NOT production)**
```yaml
# application.yml - auto-creates columns
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

---

## 📄 Files You Need

- ✅ `DEVICE_INTEGRATION_MIGRATION.sql` - Already in project root
- ✅ Updated backend code - Already compiled
- ✅ Just need to run the SQL!

---

**TL;DR:** Run `psql -d security_suite_dev -f DEVICE_INTEGRATION_MIGRATION.sql`, restart backend, problem solved.
