# Camera Feeds Implementation Guide

## Current Status

### ✅ What's Implemented

1. **Motion Event Tracking** (`/surveillance/motion-events`)
   - Records motion detection events from cameras
   - Stores camera ID, detection time, confidence score
   - Paginated list with filtering by camera/date range
   - POST endpoint for external systems to report motion

2. **Feed Status Endpoint** (`/surveillance/feed-status/{cameraId}`)
   - Returns camera operational status (ONLINE/OFFLINE)
   - Last heartbeat timestamp
   - Resolution info (hardcoded as "1080p" currently)

3. **WebSocket Infrastructure**
   - Spring WebSocket with STOMP protocol configured
   - Endpoint: `ws://localhost:8080/api/v1/ws`
   - Currently used for real-time alert broadcasting
   - Topics: `/topic/*` for broadcasts, `/queue/*` for user-specific

4. **Video Clip Management (Schema Only)**
   - Database schema exists in `COMPLETE_FEATURES_MIGRATION.sql`
   - Table: `video_clip` with fields for file URLs, duration, retention
   - **NOT YET IMPLEMENTED** - No endpoints or service code

### ❌ What's NOT Implemented

- **Live video streaming** (RTSP, HLS, WebRTC)
- **Video clip recording/playback** endpoints
- **Camera feed proxy/relay** functionality
- **Actual camera device integration**
- **Simulated video feeds** for testing

---

## Architecture Options

### Option 1: Direct RTSP/HTTP Streaming (Recommended for Small Scale)

**How it works:**
- Cameras expose RTSP/MJPEG/HTTP streams directly
- Backend stores camera stream URLs in `Device` entity
- Frontend connects directly to camera streams (or via nginx proxy)
- Backend only manages metadata and authentication

**Pros:**
- Simple backend implementation
- Low server load (no video processing)
- Real-time with minimal latency

**Cons:**
- Cameras must be network-accessible to clients
- No centralized recording or transcoding
- Harder to add analytics (facial recognition, LPR)

**Implementation:**

```java
// Add to Device entity
@Column(name = "stream_url")
private String streamUrl;  // e.g., "rtsp://camera-ip:554/stream1"

@Column(name = "stream_type")
@Enumerated(EnumType.STRING)
private StreamType streamType;  // RTSP, MJPEG, HLS

// Controller endpoint
@GetMapping("/devices/{id}/stream-url")
public StreamUrlDto getStreamUrl(@PathVariable UUID id) {
    Device device = deviceService.findById(id);
    // Return URL with time-limited token for security
    return new StreamUrlDto(
        device.getStreamUrl(),
        device.getStreamType(),
        generateStreamToken(device.getId())
    );
}
```

### Option 2: Backend Proxy with FFmpeg (Recommended for Production)

**How it works:**
- Backend proxies camera streams and transcodes on-demand
- Uses FFmpeg to convert RTSP → HLS/WebRTC
- Streams served via HTTP endpoints
- Enables recording, analytics, and centralized management

**Pros:**
- Centralized control and security
- Can record/transcode/analyze streams
- Works behind NAT/firewall
- Consistent format for all clients

**Cons:**
- High CPU/bandwidth usage on backend
- More complex setup
- Requires FFmpeg/GStreamer installation

**Dependencies needed:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.bramp.ffmpeg</groupId>
    <artifactId>ffmpeg</artifactId>
    <version>0.8.0</version>
</dependency>
```

**Implementation outline:**

```java
@RestController
@RequestMapping("/surveillance/stream")
public class StreamController {
    
    @GetMapping("/{cameraId}/hls/playlist.m3u8")
    public ResponseEntity<byte[]> getHlsPlaylist(@PathVariable UUID cameraId) {
        // Start FFmpeg process to transcode RTSP → HLS
        // Return .m3u8 playlist
    }
    
    @GetMapping("/{cameraId}/hls/{segment}")
    public ResponseEntity<byte[]> getHlsSegment(
            @PathVariable UUID cameraId,
            @PathVariable String segment) {
        // Return .ts video segment
    }
    
    @GetMapping("/{cameraId}/snapshot.jpg")
    public ResponseEntity<byte[]> getSnapshot(@PathVariable UUID cameraId) {
        // Use FFmpeg to grab single frame as JPEG
    }
}
```

### Option 3: WebRTC with Janus/Kurento (Best for Low Latency)

**How it works:**
- Use WebRTC media server (Janus Gateway, Kurento, Mediasoup)
- Backend coordinates signaling (SDP offer/answer)
- Peer-to-peer video when possible, relayed via TURN server

**Pros:**
- Sub-second latency
- Industry standard for real-time video
- Works through NAT/firewall with STUN/TURN

**Cons:**
- Complex setup (requires separate media server)
- Higher infrastructure cost
- Steep learning curve

### Option 4: Simulated Feeds for Development (Quick Start)

**How it works:**
- Generate fake video frames or use test patterns
- Serve via REST API or WebSocket
- No real cameras needed

**Implementation:**

```java
@RestController
@RequestMapping("/surveillance/simulated")
public class SimulatedStreamController {
    
    private final Random random = new Random();
    
    @GetMapping("/{cameraId}/frame.jpg")
    public ResponseEntity<byte[]> getSimulatedFrame(@PathVariable UUID cameraId) {
        // Generate test pattern or static image
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Draw test pattern
        g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
        g.fillRect(0, 0, 640, 480);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.drawString("Camera " + cameraId.toString().substring(0, 8), 50, 240);
        g.drawString(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME), 50, 280);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(baos.toByteArray());
    }
    
    @MessageMapping("/surveillance/subscribe/{cameraId}")
    @SendTo("/topic/camera/{cameraId}/frames")
    public String streamSimulatedFrames(@DestinationVariable UUID cameraId) {
        // Send base64 encoded frames via WebSocket at 1 FPS
        // Frontend decodes and displays in <img> or <video>
    }
}
```

---

## Recommended Implementation Path

### Phase 1: Simulated Feeds (1-2 days)

1. **Add simulated frame endpoint**
   ```
   GET /api/v1/surveillance/simulated/{cameraId}/frame.jpg
   ```

2. **WebSocket frame streaming**
   ```
   STOMP: /app/surveillance/subscribe/{cameraId}
   TOPIC: /topic/camera/{cameraId}/frames
   ```

3. **Frontend integration**
   - Use `<img src="..." />` with periodic refresh for simple feeds
   - Use WebSocket + Canvas for smoother playback

### Phase 2: Real Camera Integration (3-5 days)

1. **Add stream URL fields to Device entity**
   ```sql
   ALTER TABLE device 
   ADD COLUMN stream_url VARCHAR(500),
   ADD COLUMN stream_username VARCHAR(100),
   ADD COLUMN stream_password_encrypted VARCHAR(500),
   ADD COLUMN stream_type VARCHAR(20);
   ```

2. **Implement camera health checks**
   - Periodic RTSP connection test
   - Update `lastHeartbeatAt` field
   - Set status to OFFLINE if unreachable

3. **Create stream proxy endpoint** (Option 1 or 2)

### Phase 3: Video Clip Recording (5-7 days)

1. **Implement `/video-clips` endpoints**
   - List/create/get/delete operations
   - Link to motion events

2. **Background recording service**
   - Trigger recording on motion/alert
   - Use FFmpeg to capture N seconds before/after event
   - Upload to S3/local storage
   - Create `video_clip` database record

3. **Retention policy enforcement**
   - Scheduled job to delete expired clips
   - Storage quota management

### Phase 4: Advanced Features (Optional)

- Facial recognition integration
- License plate recognition
- Object detection (person, vehicle)
- PTZ (Pan-Tilt-Zoom) control
- Multi-camera grid view support

---

## Sample Frontend Integration

### Option A: MJPEG Stream (Simplest)

```typescript
// React component
function CameraFeed({ cameraId }: { cameraId: string }) {
  const [frameUrl, setFrameUrl] = useState('');
  
  useEffect(() => {
    const interval = setInterval(() => {
      // Force refresh by appending timestamp
      setFrameUrl(
        `/api/v1/surveillance/simulated/${cameraId}/frame.jpg?t=${Date.now()}`
      );
    }, 1000);
    return () => clearInterval(interval);
  }, [cameraId]);
  
  return <img src={frameUrl} alt="Camera feed" />;
}
```

### Option B: WebSocket Streaming

```typescript
import { Client } from '@stomp/stompjs';

function CameraFeedWebSocket({ cameraId }: { cameraId: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  
  useEffect(() => {
    const client = new Client({
      brokerURL: 'ws://localhost:8080/api/v1/ws',
      onConnect: () => {
        client.subscribe(`/topic/camera/${cameraId}/frames`, (message) => {
          const frameData = JSON.parse(message.body);
          // Decode base64 and draw to canvas
          const img = new Image();
          img.onload = () => {
            const ctx = canvasRef.current?.getContext('2d');
            ctx?.drawImage(img, 0, 0);
          };
          img.src = 'data:image/jpeg;base64,' + frameData.data;
        });
        
        client.publish({
          destination: `/app/surveillance/subscribe/${cameraId}`,
        });
      },
    });
    
    client.activate();
    return () => client.deactivate();
  }, [cameraId]);
  
  return <canvas ref={canvasRef} width={640} height={480} />;
}
```

### Option C: HLS.js (for HLS streams)

```typescript
import Hls from 'hls.js';

function CameraFeedHLS({ cameraId }: { cameraId: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  
  useEffect(() => {
    if (!videoRef.current) return;
    
    const hls = new Hls();
    hls.loadSource(`/api/v1/surveillance/stream/${cameraId}/hls/playlist.m3u8`);
    hls.attachMedia(videoRef.current);
    
    return () => hls.destroy();
  }, [cameraId]);
  
  return <video ref={videoRef} controls autoPlay />;
}
```

---

## Testing Without Real Cameras

### Mock RTSP Server (Using Docker)

```bash
# Run RTSP simple server with test streams
docker run --rm -d -p 8554:8554 \
  aler9/rtsp-simple-server

# Or use VLC to stream a video file as RTSP
vlc -vvv video.mp4 --sout '#rtp{sdp=rtsp://:8554/stream}' --loop
```

### Use Public Test Streams

```
rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
```

### Postman/cURL Testing

```bash
# Record motion event
curl -X POST http://localhost:8080/api/v1/surveillance/motion-events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "cameraId": "123e4567-e89b-12d3-a456-426614174000",
    "confidence": 0.95
  }'

# Get feed status
curl http://localhost:8080/api/v1/surveillance/feed-status/123e4567-e89b-12d3-a456-426614174000 \
  -H "Authorization: Bearer $TOKEN"

# List motion events
curl "http://localhost:8080/api/v1/surveillance/motion-events?cameraId=123e4567-e89b-12d3-a456-426614174000&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Security Considerations

### ✅ Implemented
- JWT authentication required for all surveillance endpoints
- Role-based access (ADMIN, SECURITY_OFFICER can POST events)

### 🔒 Recommended Additions

1. **Stream URL Encryption**
   ```java
   // Don't expose raw RTSP credentials
   // Use time-limited signed URLs
   public String generateStreamToken(UUID cameraId, long expiresInSeconds) {
       String payload = cameraId + ":" + (System.currentTimeMillis() + expiresInSeconds * 1000);
       return jwtService.sign(payload);
   }
   ```

2. **Rate Limiting**
   - Limit frame requests per user/IP
   - Prevent DoS via excessive stream connections

3. **Storage Quota**
   - Limit video clip storage per tenant
   - Enforce retention policies

4. **Audit Logging**
   - Log who viewed which camera feed and when
   - Track video clip downloads

---

## Performance Optimization

### Backend
- Use nginx/HAProxy to proxy camera streams (offload from Spring Boot)
- Enable video segment caching (CDN for HLS)
- Use GPU acceleration for FFmpeg transcoding
- Implement adaptive bitrate streaming

### Database
- Index `motion_event(camera_id, detected_at)`
- Archive old motion events to separate table
- Use TimescaleDB for time-series camera data

### Storage
- Store video clips on S3/MinIO (not local filesystem)
- Use CloudFront or CDN for clip delivery
- Compress old clips (reduce bitrate after 30 days)

---

## Next Steps

**Choose an approach:**

1. **Quick Demo**: Start with **Simulated Feeds** (Option 4)
   - I can implement this in ~1 hour
   - No external dependencies
   - Good for UI development

2. **Real Cameras**: Implement **Direct RTSP** (Option 1)
   - Add stream URL fields to Device
   - Create token-based stream proxy
   - ~2-3 days for full implementation

3. **Production Ready**: Use **FFmpeg Proxy** (Option 2)
   - More complex but production-grade
   - ~5-7 days for MVP
   - Supports recording and analytics

Which approach would you like me to implement?
