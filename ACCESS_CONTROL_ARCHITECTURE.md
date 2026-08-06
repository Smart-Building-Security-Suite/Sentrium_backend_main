# Access Control & Device Integration Architecture

## Current State Analysis

### ✅ What's Implemented

The backend has **metadata management** for access control but **NO actual device connectivity**. Here's what exists:

#### 1. Device Registry
- **Endpoint:** `POST /devices` - Register devices (cameras, access points, sensors)
- **Fields:** name, type, zone, status, lastHeartbeatAt
- **Types:** `ACCESS_POINT`, `CAMERA_SIM`, `SENSOR`
- **Operations:** Create, update, deactivate, delete, unlock command

#### 2. Access Logs
- **Endpoint:** `POST /access/logs` - Record access events
- **Records:** Who accessed which zone through which device
- **Results:** `GRANTED`, `DENIED`, `FORCED`, etc.

#### 3. Mobile QR Access
- **Endpoint:** `POST /mobile-access/tokens` - Generate temporary QR codes
- **Validation:** `POST /mobile-access/validate` - Check QR code validity
- **Features:** Time-limited, usage-limited, device/zone restrictions

#### 4. Access Rules
- Basic rule storage (doorId, requiredLevel, allowedRoles)
- **NO enforcement logic implemented**

### ❌ What's NOT Implemented

1. **Physical device communication** (MQTT, HTTP, WebSocket)
2. **Device authentication** (API keys, certificates)
3. **Real-time command execution** (unlock only logs, doesn't send to device)
4. **Device discovery/provisioning** flow
5. **Bidirectional sync** (device → backend status updates)
6. **Hardware integration layer**

---

## The Connectivity Gap

### Current Flow (Metadata Only)

```
Mobile App → Backend API → Database
                ↓
         Logs "UNLOCK" event
         (no actual hardware control)
```

### What's Missing

```
Mobile App → Backend API → Message Broker → IoT Gateway → Physical Device
                ↓              (MQTT/HTTP)        ↓              ↓
            Database                         Relay/Hub      Door Lock
                                                              Camera
```

---

## How Access Control SHOULD Work

### Architecture Options

#### **Option 1: Direct HTTP Integration (Simple, Limited Scale)**

```
Device/Gateway ←─────HTTP REST────────→ Backend
    ↓                                        ↓
Physical Lock                           Database
Camera
```

**How it works:**
1. **Device Registration:**
   - Admin adds device via `POST /devices` with IP address/endpoint
   - Backend stores device credentials (API key, shared secret)

2. **Unlock Command Flow:**
   ```
   User → Backend → HTTP POST to device endpoint
         "POST http://door-1.local/unlock" + API key
   Device → Executes unlock → Returns status
   Backend → Logs access event
   ```

3. **Device Heartbeat:**
   - Device periodically calls `POST /devices/{id}/heartbeat`
   - Backend updates `lastHeartbeatAt` and status

**Pros:**
- Simple to implement
- No additional infrastructure
- Works with RESTful smart locks

**Cons:**
- Devices must be network accessible
- No offline support
- Limited scalability (hundreds, not thousands)
- Polling-based status updates

**Implementation (Next Step):**
```java
// Add to Device entity
@Column(name = "endpoint_url")
private String endpointUrl; // e.g., "http://192.168.1.50:8080"

@Column(name = "api_key_encrypted")
private String apiKeyEncrypted;

// DeviceService.unlockDevice() enhancement
public Map<String, Object> unlockDevice(String id) {
    Device device = getById(UUID.fromString(id));
    
    // Send HTTP command to actual device
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-API-Key", decrypt(device.getApiKeyEncrypted()));
    
    HttpEntity<Map<String, String>> request = new HttpEntity<>(
        Map.of("command", "UNLOCK", "duration", "5"),
        headers
    );
    
    ResponseEntity<String> response = restTemplate.postForEntity(
        device.getEndpointUrl() + "/unlock",
        request,
        String.class
    );
    
    // Log access event
    logAccessEvent(device, "UNLOCK", response.getStatusCode().is2xxSuccessful());
    
    return Map.of("status", response.getStatusCode(), "body", response.getBody());
}
```

---

#### **Option 2: MQTT Pub/Sub (Recommended, Scalable)**

```
              ┌──────────────┐
              │ MQTT Broker  │
              │ (Mosquitto)  │
              └──────┬───────┘
                     │
        ┌────────────┼────────────┐
        ↓            ↓            ↓
    Backend      Gateway      Device
      ↓             ↓            ↓
  Subscribe    Translate    Physical
  /devices/+   protocols      Lock
```

**How it works:**
1. **Device Registration:**
   - Device connects to MQTT broker with unique client ID
   - Subscribes to `devices/{deviceId}/commands`
   - Publishes status to `devices/{deviceId}/status`

2. **Unlock Command Flow:**
   ```
   User → Backend → MQTT publish to "devices/door-1/commands"
          {
            "command": "UNLOCK",
            "duration": 5,
            "requestId": "req-123"
          }
   
   Device → Receives message → Executes unlock → Publishes response
          "devices/door-1/status/req-123"
          {
            "status": "SUCCESS",
            "timestamp": "..."
          }
   
   Backend → Subscribes to status → Logs access event
   ```

3. **Device Heartbeat:**
   - Device publishes to `devices/{deviceId}/heartbeat` every 30s
   - Backend subscribes and updates status

**Pros:**
- Scales to thousands of devices
- Bi-directional real-time communication
- Works through NAT/firewall
- QoS guarantees message delivery
- Offline message queuing

**Cons:**
- Requires MQTT broker (Mosquitto, HiveMQ)
- More complex setup
- Devices need MQTT client library

**Implementation:**

**Step 1: Add MQTT Dependency**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
</dependency>
```

**Step 2: MQTT Configuration**
```java
@Configuration
public class MqttConfig {
    @Value("${mqtt.broker.url}")
    private String brokerUrl; // tcp://localhost:1883
    
    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, "sentrium-backend-" + UUID.randomUUID());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        client.connect(options);
        return client;
    }
}
```

**Step 3: Device Command Service**
```java
@Service
@RequiredArgsConstructor
public class DeviceCommandService {
    private final MqttClient mqttClient;
    private final AccessLogRepository accessLogRepository;
    
    public CompletableFuture<CommandResponse> unlockDevice(UUID deviceId, int durationSeconds) {
        String topic = "devices/" + deviceId + "/commands";
        String requestId = UUID.randomUUID().toString();
        
        JsonObject command = new JsonObject();
        command.addProperty("command", "UNLOCK");
        command.addProperty("duration", durationSeconds);
        command.addProperty("requestId", requestId);
        
        MqttMessage message = new MqttMessage(command.toString().getBytes());
        message.setQos(1); // At least once delivery
        
        // Subscribe to response
        CompletableFuture<CommandResponse> responseFuture = new CompletableFuture<>();
        String responseTopic = "devices/" + deviceId + "/status/" + requestId;
        
        mqttClient.subscribe(responseTopic, (topic, msg) -> {
            CommandResponse response = parseResponse(new String(msg.getPayload()));
            responseFuture.complete(response);
            mqttClient.unsubscribe(responseTopic);
        });
        
        // Publish command
        mqttClient.publish(topic, message);
        
        // Timeout after 10 seconds
        responseFuture.orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(ex -> new CommandResponse(false, "Device did not respond"));
        
        return responseFuture;
    }
    
    @PostConstruct
    public void subscribeToHeartbeats() {
        // Subscribe to all device heartbeats
        mqttClient.subscribe("devices/+/heartbeat", (topic, message) -> {
            String deviceId = extractDeviceId(topic); // Parse from topic
            updateDeviceStatus(UUID.fromString(deviceId), DeviceStatus.ONLINE);
        });
    }
}
```

---

#### **Option 3: WebSocket (Good for Real-time Dashboards)**

```
Device ←───WebSocket (wss://)────→ Backend
  ↓                                    ↓
Lock                              Dashboard
                                   (Live updates)
```

**How it works:**
- Device connects to `wss://backend/device-gateway`
- Backend sends commands via WebSocket
- Device sends status updates back

**Pros:**
- Real-time bidirectional
- Works through HTTP proxies
- Good for dashboard live updates

**Cons:**
- Less scalable than MQTT
- No built-in QoS
- Connection management complexity

---

#### **Option 4: Cloud IoT Platform (AWS IoT, Azure IoT Hub)**

```
Device → AWS IoT Core → Lambda → Backend API
           ↓                        ↓
      Device Shadow            Database
      (state sync)
```

**Pros:**
- Enterprise-grade security
- Device management features
- Global scale
- Device shadow (last known state)

**Cons:**
- Vendor lock-in
- Additional cost
- More complex setup

---

## Device Provisioning Flow

### How devices get added to the system:

#### **Manual Provisioning (Current)**
1. Admin creates device via `POST /devices`
2. Backend generates device credentials (API key or client cert)
3. Admin manually configures device with credentials
4. Device connects and starts sending heartbeats

#### **Zero-Touch Provisioning (Future)**
1. Device factory-programmed with claim token
2. Device connects to backend with claim token
3. Backend verifies token and auto-creates device record
4. Exchanges claim token for permanent credentials
5. Device reconnects with permanent credentials

---

## Camera Access After Registration

### Current State: NO Automatic Connection

When you register a camera via `POST /devices`:
```json
{
  "name": "Lobby Camera",
  "type": "CAMERA_SIM",
  "zoneId": "zone-uuid"
}
```

**What happens:**
- ✅ Database record created
- ✅ Device gets UUID
- ❌ NO network discovery
- ❌ NO stream URL configured
- ❌ NO automatic connection

**To actually access the camera, you must:**
1. **Manually add stream URL** (not implemented):
   ```sql
   ALTER TABLE device ADD COLUMN stream_url VARCHAR(500);
   UPDATE device SET stream_url = 'rtsp://192.168.1.100:554/stream1' WHERE id = '...';
   ```

2. **OR use simulated feeds** (what we just implemented)

### Recommended: Camera Auto-Discovery

**Using ONVIF Protocol** (Industry standard for IP cameras):

```java
@Service
public class OnvifDiscoveryService {
    
    public List<DiscoveredCamera> scanNetwork(String subnet) {
        // Send WS-Discovery probe
        String probeMessage = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body>
                <Probe xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                  <Types>dn:NetworkVideoTransmitter</Types>
                </Probe>
              </s:Body>
            </s:Envelope>
            """;
        
        // Send to 239.255.255.250:3702 (multicast)
        // Cameras respond with their IP and RTSP URL
        
        List<DiscoveredCamera> cameras = new ArrayList<>();
        // Parse responses...
        return cameras;
    }
    
    public CameraInfo getCameraInfo(String ip, String username, String password) {
        // Connect to http://{ip}/onvif/device_service
        // Get device information, capabilities, stream URLs
        OnvifDevice device = new OnvifDevice(ip, username, password);
        String streamUrl = device.getStreamUri();
        return new CameraInfo(device.getName(), streamUrl, device.getCapabilities());
    }
}
```

**Dependency:**
```xml
<dependency>
    <groupId>de.onvif</groupId>
    <artifactId>onvif</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Flow:**
1. Admin clicks "Scan for cameras" in UI
2. Frontend calls `GET /devices/scan?subnet=192.168.1.0/24`
3. Backend discovers ONVIF cameras on network
4. Returns list of discovered cameras with IPs
5. Admin selects cameras to add
6. Backend creates device records with stream URLs
7. Cameras now accessible for viewing/recording

---

## Smart Lock Integration Examples

### Example 1: ESP32-based Lock (HTTP)

**Device Setup:**
```cpp
// ESP32 firmware
#include <WiFi.h>
#include <HTTPClient.h>

const char* backendUrl = "https://backend.sentrium.com/api/v1/devices";
const char* apiKey = "device-api-key-here";
const char* deviceId = "esp32-lock-001";

void setup() {
  // Connect to WiFi
  WiFi.begin("SSID", "password");
  
  // Register with backend
  registerDevice();
  
  // Start listening for commands
  xTaskCreate(listenForCommands, "Commands", 4096, NULL, 1, NULL);
  xTaskCreate(sendHeartbeat, "Heartbeat", 4096, NULL, 1, NULL);
}

void registerDevice() {
  HTTPClient http;
  http.begin(backendUrl + "/register");
  http.addHeader("X-API-Key", apiKey);
  http.POST("{\"deviceId\":\"" + String(deviceId) + "\",\"type\":\"ACCESS_POINT\"}");
}

void listenForCommands(void* param) {
  while(true) {
    // Poll backend for commands
    HTTPClient http;
    http.begin(backendUrl + "/" + deviceId + "/commands");
    http.addHeader("X-API-Key", apiKey);
    int httpCode = http.GET();
    
    if(httpCode == 200) {
      String payload = http.getString();
      if(payload.indexOf("UNLOCK") >= 0) {
        unlockDoor();
        reportStatus("UNLOCKED");
      }
    }
    
    delay(2000); // Poll every 2 seconds
  }
}

void unlockDoor() {
  digitalWrite(LOCK_PIN, HIGH);
  delay(5000);
  digitalWrite(LOCK_PIN, LOW);
}

void reportStatus(String status) {
  HTTPClient http;
  http.begin(backendUrl + "/" + deviceId + "/status");
  http.addHeader("X-API-Key", apiKey);
  http.POST("{\"status\":\"" + status + "\"}");
}
```

### Example 2: Raspberry Pi Gateway (MQTT)

**Gateway Setup:**
```python
# Raspberry Pi running Python
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO
import json

BROKER = "mqtt.sentrium.com"
DEVICE_ID = "rpi-gate-001"
LOCK_PIN = 17

def on_connect(client, userdata, flags, rc):
    print(f"Connected with result code {rc}")
    client.subscribe(f"devices/{DEVICE_ID}/commands")

def on_message(client, userdata, msg):
    command = json.loads(msg.payload)
    
    if command['command'] == 'UNLOCK':
        unlock_door(command.get('duration', 5))
        
        # Report status
        client.publish(
            f"devices/{DEVICE_ID}/status/{command['requestId']}",
            json.dumps({"status": "SUCCESS", "timestamp": time.time()})
        )

def unlock_door(duration):
    GPIO.output(LOCK_PIN, GPIO.HIGH)
    time.sleep(duration)
    GPIO.output(LOCK_PIN, GPIO.LOW)

def send_heartbeat(client):
    while True:
        client.publish(
            f"devices/{DEVICE_ID}/heartbeat",
            json.dumps({"status": "ONLINE", "timestamp": time.time()})
        )
        time.sleep(30)

# Setup
GPIO.setmode(GPIO.BCM)
GPIO.setup(LOCK_PIN, GPIO.OUT)

client = mqtt.Client(DEVICE_ID)
client.on_connect = on_connect
client.on_message = on_message

client.connect(BROKER, 1883, 60)

# Start heartbeat thread
threading.Thread(target=send_heartbeat, args=(client,), daemon=True).start()

# Start listening
client.loop_forever()
```

---

## QR Code Access Flow

### Current Implementation (Metadata Only)

1. **Generate QR Code:**
   ```bash
   POST /mobile-access/tokens
   {
     "userId": "user-uuid",
     "deviceId": "door-uuid",
     "durationMinutes": 60,
     "maxUses": 1
   }
   ```
   
   Response:
   ```json
   {
     "id": "token-uuid",
     "qrCodeData": "QR-a1b2c3d4-...",
     "expiresAt": "2026-08-06T18:00:00Z"
   }
   ```

2. **User shows QR code at door**

3. **Device/gateway validates:**
   ```bash
   POST /mobile-access/validate
   {
     "qrCodeData": "QR-a1b2c3d4-...",
     "deviceId": "door-uuid"
   }
   ```
   
   Response:
   ```json
   {
     "granted": true,
     "message": "Access granted",
     "userId": "user-uuid",
     "userName": "John Doe"
   }
   ```

4. **Device unlocks door** (device must implement this!)

### What's Missing: Device Integration

The backend says "Access granted" but **doesn't actually unlock the door**.

**To complete the flow:**

**Option A: Device polls backend**
```java
// Device firmware
while(true) {
  String qrCode = readQRScanner();
  if(qrCode != null) {
    boolean granted = validateQRCode(qrCode, deviceId);
    if(granted) {
      unlockDoor();
    }
  }
  delay(100);
}
```

**Option B: Backend pushes to device (MQTT)**
```java
// After validation succeeds
if(result.granted()) {
  mqttClient.publish(
    "devices/" + deviceId + "/commands",
    new MqttMessage("{\"command\":\"UNLOCK\",\"duration\":5}".getBytes())
  );
}
```

---

## Implementation Roadmap

### Phase 1: HTTP Device Integration (3-5 days)

1. **Add device endpoint fields**
   ```sql
   ALTER TABLE device 
   ADD COLUMN endpoint_url VARCHAR(500),
   ADD COLUMN api_key_encrypted VARCHAR(500),
   ADD COLUMN connection_protocol VARCHAR(20);
   ```

2. **Implement HTTP command sender**
   - `DeviceCommandService.sendUnlockCommand(deviceId)`
   - Encrypt/decrypt API keys
   - Handle timeouts and retries

3. **Device heartbeat receiver**
   - Endpoint: `POST /devices/{id}/heartbeat` (already exists)
   - Update `lastHeartbeatAt` and status

4. **Test with simulated device**
   - Python script that listens for HTTP commands
   - Responds to unlock requests

### Phase 2: MQTT Integration (5-7 days)

1. **Setup MQTT broker**
   ```bash
   docker run -d -p 1883:1883 -p 9001:9001 eclipse-mosquitto
   ```

2. **Add MQTT service**
   - Subscribe to device heartbeats
   - Publish commands
   - Handle responses

3. **Device provisioning API**
   - Generate MQTT credentials
   - Return connection details

4. **Test with ESP32/Raspberry Pi**

### Phase 3: Camera Auto-Discovery (3-4 days)

1. **Add ONVIF library**
2. **Implement network scanner**
3. **Camera registration wizard**
4. **Automatic stream URL configuration**

### Phase 4: WebSocket Alternative (2-3 days)

1. **Device gateway WebSocket endpoint**
2. **Command/status message protocol**
3. **Connection management**

---

## Security Considerations

### Device Authentication

**Current:** None (relies on Spring Security user auth)

**Needed:**
1. **API Keys** - Per-device unique keys
   ```java
   @Column(name = "api_key_hash")
   private String apiKeyHash; // bcrypt hash
   ```

2. **TLS Client Certificates** - For MQTT/HTTP
   ```java
   MqttConnectOptions options = new MqttConnectOptions();
   options.setSocketFactory(sslContext.getSocketFactory());
   ```

3. **Device Token Exchange**
   - Claim token → Permanent credentials
   - Automatic rotation

### Network Security

1. **TLS everywhere** - HTTPS, MQTTS, WSS
2. **Firewall rules** - Only allow backend → device, not public
3. **VPN/Private network** - Keep devices off public internet
4. **Rate limiting** - Prevent brute force on QR validation

---

## Summary

### Current State
- ✅ Metadata management (devices, logs, QR tokens)
- ✅ API endpoints for control commands
- ❌ **NO physical device communication**
- ❌ NO device authentication
- ❌ NO auto-discovery

### The Gap
Backend can **record** that an unlock happened, but can't **make** it happen.

### Next Steps
**Choose integration method:**
1. **HTTP** (easiest) - 3-5 days
2. **MQTT** (recommended) - 5-7 days  
3. **WebSocket** (real-time) - 2-3 days
4. **Cloud IoT** (enterprise) - 10-14 days

**Quick win:** Start with HTTP for 1-2 test devices, migrate to MQTT for scale.

---

Would you like me to implement **HTTP device integration** as Phase 1?
