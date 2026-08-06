# Deployment Guide - Sentrium Backend (Neon + Render)

## Prerequisites
- [x] Neon PostgreSQL database created
- [ ] Render account created
- [ ] GitHub repository connected to Render

## Step-by-Step Deployment

### 1. Neon Database Setup ✓ COMPLETE

Your Neon database is ready:
- **Database**: neondb
- **Region**: us-east-2 (Ohio)
- **Connection**: Pooled connection enabled

**What happens automatically:**
- Tables will be created when your app first deploys
- No manual SQL needed - Hibernate will handle it

**After first deployment, check Neon:**
1. Go to your Neon project dashboard
2. Click on "Tables" to see all created tables
3. You should see: users, visitors, access_logs, zones, emergency_events, etc.

---

### 2. Generate Secrets

Before deploying, generate secure secrets:

```bash
# Generate JWT Secret (64 characters)
openssl rand -base64 64

# Save this output - you'll need it for Render
```

---

### 3. Deploy to Render

#### Option A: Deploy via render.yaml (Recommended)

1. **Push your code to GitHub**
   ```bash
   git add .
   git commit -m "feat: add Render deployment configuration"
   git push origin main
   ```

2. **Connect to Render**
   - Go to [render.com](https://render.com)
   - Click "New +" → "Blueprint"
   - Connect your GitHub repository
   - Render will auto-detect `render.yaml`

3. **Set Secret Environment Variables**
   
   In Render dashboard, go to your service → Environment:
   
   ```bash
   DATABASE_PASSWORD=npg_l5wxpIHPYBF9
   JWT_SECRET=<paste the output from openssl command>
   ```

   Optional (if using email features):
   ```bash
   MAIL_USERNAME=your-email@gmail.com
   MAIL_PASSWORD=your-app-password
   ```

4. **Update CORS Origins**
   
   In Render dashboard, update the `APP_CORS_ALLOWED_ORIGINS` env var:
   ```
   APP_CORS_ALLOWED_ORIGINS=https://your-frontend-url.vercel.app,https://your-frontend-url.render.com
   ```

5. **Deploy**
   - Click "Create Web Service" or "Apply"
   - Render will build and deploy your Docker container
   - First build takes ~5-10 minutes

#### Option B: Manual Setup (Alternative)

1. Go to [render.com](https://render.com)
2. Click "New +" → "Web Service"
3. Connect your repository
4. Configure:
   - **Name**: sentrium-backend
   - **Runtime**: Docker
   - **Region**: Oregon (or closest to you)
   - **Instance Type**: Free or Starter
   - **Dockerfile Path**: ./Dockerfile
5. Add all environment variables from `render.yaml`
6. Click "Create Web Service"

---

### 4. Verify Deployment

Once deployed, your backend will be available at:
```
https://sentrium-backend.onrender.com
```

**Test endpoints:**

1. **Health Check**
   ```bash
   curl https://your-app.onrender.com/api/v1/actuator/health
   ```
   Should return: `{"status":"UP"}`

2. **API Documentation** (if enabled)
   ```
   https://your-app.onrender.com/api/v1/swagger-ui.html
   ```

3. **Test Authentication**
   ```bash
   curl -X POST https://your-app.onrender.com/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{
       "email": "test@example.com",
       "password": "TestPassword123!",
       "firstName": "Test",
       "lastName": "User",
       "phoneNumber": "+1234567890"
     }'
   ```

---

### 5. Verify Database Tables in Neon

After first successful deployment:

1. Go to your Neon dashboard
2. Navigate to: **Your Project** → **Tables** → **neondb**
3. You should see tables created:
   - `users`
   - `visitors`
   - `access_logs`
   - `zones`
   - `emergency_events`
   - `emergency_contacts`
   - `motion_events`
   - `anomalies`
   - `access_rules`
   - `revoked_tokens`

4. **Query Example** (in Neon SQL Editor):
   ```sql
   SELECT table_name 
   FROM information_schema.tables 
   WHERE table_schema = 'public';
   ```

---

### 6. Post-Deployment Configuration

#### A. Switch to Validate Mode (After First Deploy)

Once tables are created, update Render env var:
```
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

This prevents accidental schema changes in production.

#### B. Set Up Neon Backups

1. Go to Neon Dashboard → Your Project → Settings
2. Enable **Automated Backups**
3. Configure retention period

#### C. Monitor Your App

**Render Dashboard:**
- View logs: Service → Logs
- Monitor metrics: Service → Metrics
- Set up alerts: Service → Notifications

**Neon Dashboard:**
- Query performance: Monitoring → Queries
- Connection pooling stats: Monitoring → Connections
- Database size: Project → Usage

---

### 7. Connect Your Frontend

Update your frontend (React Native/Expo) with the backend URL:

```javascript
// config.js or .env
const API_URL = 'https://sentrium-backend.onrender.com/api/v1';

// Example API call
const response = await fetch(`${API_URL}/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password123'
  })
});
```

---

## Troubleshooting

### Issue: Tables not created

**Solution:**
1. Check Render logs: `Service → Logs`
2. Look for Hibernate messages like "Creating table..."
3. Verify `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
4. Check database connection in logs

### Issue: Connection timeout to Neon

**Solution:**
1. Verify `DATABASE_URL` includes `?sslmode=require`
2. Check Neon database is active (not suspended)
3. Verify connection string has `-pooler` endpoint

### Issue: CORS errors in frontend

**Solution:**
1. Update `APP_CORS_ALLOWED_ORIGINS` in Render
2. Include all frontend URLs (with https://)
3. No trailing slashes in URLs
4. Redeploy after changing env vars

### Issue: 503 Service Unavailable

**Solution:**
- Render free tier spins down after 15 min of inactivity
- First request after spin-down takes ~30-60 seconds
- Upgrade to paid plan for always-on service

### Issue: Out of memory / App crashing

**Solution:**
1. Check Render logs for `OutOfMemoryError`
2. Upgrade to a larger instance type (Starter+)
3. Optimize JVM settings in Dockerfile (already configured)

---

## Scaling & Production Best Practices

### 1. Database
- [ ] Set up read replicas in Neon (if needed)
- [ ] Enable connection pooling (already done with `-pooler`)
- [ ] Monitor query performance
- [ ] Set up automated backups

### 2. Application
- [ ] Set `ddl-auto: validate` after initial setup
- [ ] Enable proper logging (ELK, DataDog, etc.)
- [ ] Set up monitoring/alerting
- [ ] Configure rate limiting per environment
- [ ] Use environment-specific secrets

### 3. Security
- [ ] Rotate JWT secrets periodically
- [ ] Use strong database passwords
- [ ] Enable SSL for all connections (already configured)
- [ ] Set up WAF (Cloudflare, AWS WAF)
- [ ] Regular security audits

### 4. Performance
- [ ] Enable database indexes on frequently queried fields
- [ ] Use caching (Redis) for frequently accessed data
- [ ] Set up CDN for static assets
- [ ] Monitor and optimize slow queries

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Yes | - | Neon PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes | - | Database username |
| `DATABASE_PASSWORD` | Yes | - | Database password |
| `JWT_SECRET` | Yes | - | Secret key for JWT signing (min 64 chars) |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | No | 900000 | 15 minutes |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | No | 604800000 | 7 days |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | No | update | update, validate, or none |
| `PORT` | No | 8080 | Server port (Render provides this) |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | - | Comma-separated frontend URLs |
| `MAIL_HOST` | No | - | SMTP host (if using email) |
| `MAIL_PORT` | No | 587 | SMTP port |
| `MAIL_USERNAME` | No | - | Email username |
| `MAIL_PASSWORD` | No | - | Email password/app password |
| `APP_REPORTS_DIR` | No | /tmp/reports | Directory for generated reports |

---

## Cost Estimates

### Neon (Free Tier)
- ✅ 512 MB storage
- ✅ Unlimited compute hours on Free tier
- ✅ Connection pooling included
- 💰 Paid plans start at $19/month

### Render (Free Tier)
- ✅ 512 MB RAM
- ✅ Shared CPU
- ⚠️ Spins down after 15 min inactivity
- ⚠️ 750 hours/month free (then spins down completely)
- 💰 Starter plan: $7/month (always-on, more resources)

**Total for Free Tier**: $0/month  
**Total for Production**: ~$26/month (Neon Launch + Render Starter)

---

## Support & Resources

- **Neon Docs**: https://neon.tech/docs
- **Render Docs**: https://render.com/docs
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **GitHub Repo**: [Your Repository URL]

---

## Quick Commands

```bash
# Local development
cp .env.example .env
./mvnw spring-boot:run

# Build Docker locally
docker build -t sentrium-backend .
docker run -p 8080:8080 --env-file .env sentrium-backend

# Deploy to Render
git add .
git commit -m "your message"
git push origin main
# Render auto-deploys from main branch

# Check Render logs
# Go to: render.com → Your Service → Logs

# Query Neon database
# Go to: neon.tech → Your Project → SQL Editor
```

---

🎉 **Deployment Complete!** Your backend is now live on Render with Neon PostgreSQL.
