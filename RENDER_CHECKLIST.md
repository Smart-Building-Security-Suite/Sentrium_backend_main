# 🚀 Render Deployment Checklist

## Pre-Deployment (Do this first)

- [ ] **Generate JWT Secret**
  ```bash
  openssl rand -base64 64
  ```
  Copy the output - you'll paste this into Render

- [ ] **Copy .env file for local testing**
  ```bash
  cp .env.example .env
  ```

- [ ] **Test locally** (optional but recommended)
  ```bash
  ./mvnw spring-boot:run
  ```
  Visit: http://localhost:8080/api/v1/actuator/health

- [ ] **Commit and push to GitHub**
  ```bash
  git add .
  git commit -m "feat: add Render deployment configuration"
  git push origin main
  ```

---

## Render Setup

### Step 1: Create New Web Service

1. Go to [render.com](https://render.com) and sign in
2. Click **"New +"** → **"Blueprint"**
3. Connect your GitHub account (if not already)
4. Select your repository: `Sentrium_backend_main`
5. Render will detect `render.yaml` automatically

### Step 2: Set Secret Environment Variables

Before clicking "Apply", you need to set these secrets:

Go to the environment variables section and add:

```bash
DATABASE_PASSWORD=npg_l5wxpIHPYBF9
JWT_SECRET=<paste your generated secret here>
```

**Optional** (only if using email features):
```bash
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
```

### Step 3: Update CORS Origins

In the environment variables, find `APP_CORS_ALLOWED_ORIGINS` and update with your frontend URL:

```
APP_CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app
```

Or if you have multiple:
```
APP_CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app,https://another-frontend.com,exp://192.168.1.100:8081
```

### Step 4: Deploy

- [ ] Click **"Apply"** or **"Create Web Service"**
- [ ] Wait for the build to complete (~5-10 minutes)
- [ ] Copy your app URL: `https://your-app-name.onrender.com`

---

## Post-Deployment

### Verify Deployment

- [ ] **Health Check**
  ```bash
  curl https://your-app-name.onrender.com/api/v1/actuator/health
  ```
  Expected: `{"status":"UP"}`

- [ ] **Test Registration**
  ```bash
  curl -X POST https://your-app-name.onrender.com/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "email": "test@example.com",
      "password": "TestPassword123!",
      "firstName": "Test",
      "lastName": "User",
      "phoneNumber": "+1234567890"
    }'
  ```

### Check Database Tables in Neon

1. [ ] Go to [Neon Dashboard](https://neon.tech)
2. [ ] Navigate to your project
3. [ ] Click **"Tables"** in the left sidebar
4. [ ] Verify tables were created:
   - users
   - visitors
   - access_logs
   - zones
   - emergency_events
   - motion_events
   - etc.

### Switch to Validate Mode

After verifying tables are created:

1. [ ] Go to Render Dashboard → Your Service → **Environment**
2. [ ] Find `SPRING_JPA_HIBERNATE_DDL_AUTO`
3. [ ] Change value from `update` to `validate`
4. [ ] Click **"Save Changes"**
5. [ ] Service will auto-redeploy

---

## Update Your Frontend

- [ ] Update your frontend API URL to point to Render:
  ```javascript
  const API_URL = 'https://your-app-name.onrender.com/api/v1';
  ```

- [ ] Test authentication flow from your frontend
- [ ] Verify CORS is working (no CORS errors in browser console)

---

## Monitoring

### Render Logs
- [ ] Bookmark: `https://dashboard.render.com/web/<your-service-id>/logs`
- [ ] Check for any errors or warnings

### Neon Monitoring
- [ ] Go to Neon → Your Project → **Monitoring**
- [ ] Check connection count
- [ ] Monitor query performance

---

## Optional: Set Up Custom Domain

1. [ ] Go to Render Dashboard → Your Service → **Settings**
2. [ ] Scroll to **Custom Domain**
3. [ ] Click **"Add Custom Domain"**
4. [ ] Follow Render's instructions to update DNS

---

## Troubleshooting

### ❌ Build Failed
- Check Render logs for errors
- Verify Dockerfile syntax
- Ensure pom.xml has no issues

### ❌ Application won't start
- Check environment variables are set correctly
- Verify `DATABASE_URL` format
- Check `JWT_SECRET` is set

### ❌ Database connection failed
- Verify Neon database is active (not suspended)
- Check connection string includes `?sslmode=require`
- Verify username/password are correct

### ❌ CORS errors from frontend
- Update `APP_CORS_ALLOWED_ORIGINS` with correct frontend URL
- Include `https://` protocol
- No trailing slashes
- Multiple origins separated by commas (no spaces)

### ⚠️ Service is slow (free tier)
- Free tier spins down after 15 min inactivity
- First request takes 30-60 seconds to spin up
- Consider upgrading to Starter plan ($7/mo) for always-on

---

## Quick Reference

| Item | Value |
|------|-------|
| **Your Render URL** | https://sentrium-backend.onrender.com |
| **Health Check** | /api/v1/actuator/health |
| **API Base Path** | /api/v1 |
| **Neon Database** | ep-falling-shape-ay6u866f-pooler.c-5.us-east-2.aws.neon.tech |
| **Render Logs** | Dashboard → Your Service → Logs |
| **Neon Dashboard** | https://neon.tech |

---

## Next Steps After Deployment

1. [ ] Set up monitoring/alerting in Render
2. [ ] Enable Neon automated backups
3. [ ] Configure CI/CD for automatic deployments
4. [ ] Set up staging environment
5. [ ] Add custom domain
6. [ ] Monitor costs and usage

---

## Need Help?

- **Render Docs**: https://render.com/docs
- **Neon Docs**: https://neon.tech/docs
- **Render Support**: support@render.com
- **Neon Support**: https://neon.tech/docs/introduction/support

---

✅ **Checklist Complete!** Your app should now be live on Render with Neon database.
