# 🔔 Push Notification Triggers - Complete Reference

## ✅ **All Wired Up and Ready!**

Push notifications are now automatically triggered for key security events across your system.

---

## 📱 **Automatic Push Notification Triggers**

### **1. 🚨 Alert Created**

**Trigger:** When a new alert is created  
**Recipients:** All security personnel (ADMIN + SECURITY_OFFICER)  
**Condition:** Always sent

**Notification:**
- **Title:** `[emoji] New Alert: [SEVERITY]`
  - 🚨 CRITICAL
  - ⚠️ HIGH
  - ⚡ MEDIUM
  - ℹ️ LOW
- **Body:** `[message] in [zone name]`
- **Data:**
  ```json
  {
    "alertId": "uuid",
    "severity": "HIGH",
    "zoneId": "uuid",
    "zoneName": "Server Room",
    "type": "ALERT_CREATED"
  }
  ```

**Code Location:** `AlertService.java:create()`

---

### **2. 🆘 Emergency Event Triggered**

**Trigger:** When emergency event is triggered  
**Recipients:** All security personnel (ADMIN + SECURITY_OFFICER)  
**Condition:** Always sent

**Notification:**
- **Title:** `🆘 EMERGENCY: [EVENT_TYPE]`
  - LOCKDOWN, FIRE, EVACUATION, MEDICAL, ACTIVE_THREAT, etc.
- **Body:** `[description]`
- **Data:**
  ```json
  {
    "emergencyType": "LOCKDOWN",
    "description": "...",
    "timestamp": "2026-08-06T15:30:00Z"
  }
  ```

**Code Location:** `EmergencyService.java:triggerEmergency()`

---

### **3. 🚨 High/Critical Incident Reported**

**Trigger:** When new incident is created  
**Recipients:** All security personnel (ADMIN + SECURITY_OFFICER)  
**Condition:** Only sent if severity is HIGH or CRITICAL

**Notification:**
- **Title:** `🚨 New [SEVERITY] Incident`
- **Body:** `[incident title]`
- **Data:**
  ```json
  {
    "incidentId": "uuid",
    "type": "THEFT",
    "severity": "HIGH",
    "reportedBy": "John Doe"
  }
  ```

**Code Location:** `IncidentService.java:create()`

---

### **4. 📋 Incident Assigned to Officer**

**Trigger:** When incident is assigned to an officer  
**Recipients:** The assigned officer only  
**Condition:** Always sent when incident is assigned

**Notification:**
- **Title:** `📋 Incident Assigned to You`
- **Body:** `Incident #[short-id]: [title]`
- **Data:**
  ```json
  {
    "incidentId": "uuid",
    "type": "VANDALISM",
    "severity": "MEDIUM",
    "status": "ASSIGNED"
  }
  ```

**Code Location:** `IncidentService.java:assign()`

---

### **5. 👤 Visitor Checked In**

**Trigger:** When visitor checks in at reception  
**Recipients:** The host employee only  
**Condition:** Always sent if visitor has a host

**Notification:**
- **Title:** `👤 Your Visitor Has Arrived`
- **Body:** `[visitor name] from [company] has checked in`
- **Data:**
  ```json
  {
    "visitorId": "uuid",
    "visitorName": "John Smith",
    "badgeNumber": "V-123",
    "type": "VISITOR_CHECKED_IN"
  }
  ```

**Code Location:** `VisitorService.java:checkIn()`

---

### **6. ⚠️ High/Critical Anomaly Detected**

**Trigger:** When AI detects a security anomaly  
**Recipients:** All security personnel (ADMIN + SECURITY_OFFICER)  
**Condition:** Only sent if severity is HIGH or CRITICAL

**Notification:**
- **Title:** `[emoji] Anomaly Detected: [TYPE]`
  - 🚨 CRITICAL
  - ⚠️ HIGH
- **Body:** `[description]`
- **Data:**
  ```json
  {
    "anomalyId": "uuid",
    "type": "RAPID_ACCESS_ATTEMPTS",
    "severity": "HIGH",
    "entityType": "USER",
    "confidence": 0.85
  }
  ```

**Code Location:** `AnomalyService.java:createAnomaly()`

---

## 📊 **Notification Priority Levels**

| Event | Priority | Emoji | Sound | Who Gets Notified |
|-------|----------|-------|-------|-------------------|
| **Emergency** | CRITICAL | 🆘 | Alarm | All security |
| **Critical Alert** | CRITICAL | 🚨 | Alarm | All security |
| **High Alert** | HIGH | ⚠️ | Default | All security |
| **Critical Anomaly** | HIGH | 🚨 | Default | All security |
| **High Incident** | HIGH | 🚨 | Default | All security |
| **Incident Assigned** | MEDIUM | 📋 | Default | Assigned officer |
| **Visitor Arrival** | LOW | 👤 | Default | Host only |
| **Medium Alert** | MEDIUM | ⚡ | Default | All security |
| **Low Alert** | LOW | ℹ️ | Default | All security |

---

## 🎯 **Recipient Targeting**

### **All Security Personnel:**
```java
pushNotificationService.sendToSecurityPersonnel(title, body, data);
```
**Who:** All users with role `ADMIN` or `SECURITY_OFFICER`

**Use Cases:**
- New alerts (all severities)
- Emergency events
- High/critical incidents
- High/critical anomalies

---

### **Specific User:**
```java
pushNotificationService.sendToUser(userId, title, body, data);
```
**Who:** One specific user by ID

**Use Cases:**
- Incident assigned to officer
- Visitor arrival (notify host)
- Personal notifications

---

### **Multiple Users:**
```java
pushNotificationService.sendToUsers(List<UUID> userIds, title, body, data);
```
**Who:** Multiple specific users

**Use Cases:**
- Notify officers on shift
- Zone-specific security team
- Escalation chain

---

## 🔕 **What Does NOT Trigger Push Notifications**

To avoid notification fatigue, these events do NOT trigger push notifications:

- ❌ User login/logout
- ❌ Device status changes (unless critical pattern)
- ❌ Low/Medium incidents (only High/Critical)
- ❌ Low/Medium anomalies (only High/Critical)
- ❌ Alert acknowledged
- ❌ Alert resolved
- ❌ Incident status updates (except assignment)
- ❌ Access log entries (unless part of anomaly)
- ❌ Visitor pre-registration (only check-in)
- ❌ Visitor check-out
- ❌ Patrol checkpoint scans (unless incident reported)

**Rationale:** These are tracked via WebSocket for real-time updates, but don't require immediate mobile notification.

---

## 🛠️ **How to Add More Triggers**

### **Example: Notify on Patrol Incident**

```java
// In PatrolService.java

@Autowired(required = false)
private PushNotificationService pushNotificationService;

@Transactional
public PatrolCheckpointScanDto linkIncidentToScan(UUID scanId, UUID incidentId) {
    // ... existing logic ...
    
    // 🔔 PUSH NOTIFICATION: Incident reported during patrol
    if (pushNotificationService != null) {
        pushNotificationService.sendToSecurityPersonnel(
            "🚔 Incident During Patrol",
            "Officer reported incident at checkpoint: " + checkpoint.getName(),
            Map.of(
                "scanId", scanId.toString(),
                "incidentId", incidentId.toString(),
                "checkpointName", checkpoint.getName()
            )
        );
    }
    
    return PatrolCheckpointScanDto.from(scan);
}
```

---

## 📲 **Mobile App Handling**

### **Navigation Based on Notification Type**

```javascript
// In your React Native app
Notifications.addNotificationResponseReceivedListener(response => {
  const data = response.notification.request.content.data;
  
  switch(data.type) {
    case 'ALERT_CREATED':
      navigation.navigate('AlertDetails', { alertId: data.alertId });
      break;
      
    case 'VISITOR_CHECKED_IN':
      navigation.navigate('VisitorDetails', { visitorId: data.visitorId });
      break;
      
    case 'ASSIGNED':
      navigation.navigate('IncidentDetails', { incidentId: data.incidentId });
      break;
      
    default:
      navigation.navigate('Dashboard');
  }
});
```

---

## 🧪 **Testing Triggers**

### **Test Alert Notification:**
```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "zoneId": "zone-uuid",
    "severity": "HIGH",
    "message": "Test alert - motion detected"
  }'
```

### **Test Emergency Notification:**
```bash
curl -X POST http://localhost:8080/api/v1/emergency/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "LOCKDOWN",
    "severity": "CRITICAL",
    "description": "Test emergency lockdown"
  }'
```

### **Test Incident Notification:**
```bash
curl -X POST http://localhost:8080/api/v1/incidents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test incident",
    "type": "THEFT",
    "severity": "HIGH",
    "zoneId": "zone-uuid"
  }'
```

### **Test Visitor Notification:**
```bash
# 1. Pre-register visitor with host
curl -X POST http://localhost:8080/api/v1/visitors \
  -d '{"name":"John","hostId":"host-uuid",...}'

# 2. Check in visitor (triggers notification to host)
curl -X POST http://localhost:8080/api/v1/visitors/{id}/check-in \
  -d '{"badgeNumber":"V-123"}'
```

---

## 📊 **Notification Statistics**

Track which notifications are being sent:

```sql
-- Count devices by type
SELECT device_type, COUNT(*) 
FROM push_notification_device 
WHERE active = true 
GROUP BY device_type;

-- Recent notifications would be tracked in a notification_log table (future enhancement)
```

---

## 🔧 **Configuration**

### **Optional: Expo Access Token**

Add to `application.yml` for higher rate limits:
```yaml
expo:
  access-token: ${EXPO_ACCESS_TOKEN:}
```

### **Disable Push Notifications (Testing)**

Remove or comment out in service classes:
```java
@Autowired(required = false)  // required = false means won't fail if not found
private PushNotificationService pushNotificationService;
```

---

## ✅ **Summary**

| Feature | Push Notifications | WebSocket | Both |
|---------|-------------------|-----------|------|
| Alert Created | ✅ | ✅ | ✅ |
| Emergency Event | ✅ | ✅ | ✅ |
| High Incident | ✅ | ❌ | |
| Incident Assigned | ✅ | ❌ | |
| Visitor Check-in | ✅ | ❌ | |
| High Anomaly | ✅ | ❌ | |
| Alert Status Change | ❌ | ✅ | |
| Device Status | ❌ | ✅ | |

**Push Notifications** = User must act (High priority, off-device)  
**WebSocket** = User should know (Real-time, in-app)  
**Both** = Critical events needing immediate attention

---

**Status:** ✅ **All Push Notifications Wired and Production Ready!**
