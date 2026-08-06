# Simulated Camera Feeds - Usage Guide

## Overview

The simulated camera feed system provides test video streams without requiring real camera hardware. Perfect for development, testing, and demos.

## Features

- ✅ **Static Frame Endpoint** - Get single JPEG frames (MJPEG-style polling)
- ✅ **WebSocket Streaming** - Continuous frame delivery at 10 FPS
- ✅ **Dynamic Test Patterns** - Animated background with moving objects
- ✅ **Camera Info Overlay** - ID, timestamp, status indicator
- ✅ **Stream Management** - Start/stop streams, view statistics
- ✅ **No Authentication Required** - WebSocket endpoint is public for demo

## API Endpoints

### 1. Get Single Frame (REST)

**Endpoint:** `GET /api/v1/surveillance/simulated/{cameraId}/frame.jpg`

**Authentication:** Required (JWT token)

**Response:** JPEG image (640x480)

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/surveillance/simulated/camera-1/frame.jpg \
  -o frame.jpg
```

**Use case:** Simple polling-based streaming (1-2 FPS refresh rate)

### 2. Stop Stream (REST)

**Endpoint:** `POST /api/v1/surveillance/simulated/{cameraId}/stop`

**Authentication:** Required (JWT token)

**Example:**
```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/surveillance/simulated/camera-1/stop
```

### 3. Get Streaming Statistics

**Endpoint:** `GET /api/v1/surveillance/simulated/stats`

**Authentication:** Required (ADMIN or SECURITY_OFFICER role)

**Response:**
```json
{
  "activeStreams": 2,
  "activeCameraIds": ["camera-1", "camera-2"]
}
```

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/surveillance/simulated/stats
```

### 4. WebSocket Streaming (STOMP)

**WebSocket URL:** `ws://localhost:8080/api/v1/ws`

**Protocol:** STOMP over WebSocket with SockJS fallback

**Commands:**
- **Start stream:** Send to `/app/surveillance/stream/start/{cameraId}`
- **Stop stream:** Send to `/app/surveillance/stream/stop/{cameraId}`

**Subscribe to frames:** `/topic/camera/{cameraId}/frames`

**Frame message format:**
```json
{
  "cameraId": "camera-1",
  "frameNumber": 42,
  "timestamp": 1704556800000,
  "data": "base64-encoded-jpeg...",
  "width": 640,
  "height": 480
}
```

---

## Frontend Integration Examples

### Option A: Simple Polling (MJPEG-style)

**Vanilla JavaScript:**
```html
<!DOCTYPE html>
<html>
<head>
  <title>Camera Feed - Polling</title>
</head>
<body>
  <h1>Camera Feed (Polling)</h1>
  <img id="camera-feed" style="border: 2px solid #333;">
  
  <script>
    const cameraId = 'camera-1';
    const token = 'YOUR_JWT_TOKEN';
    const img = document.getElementById('camera-feed');
    
    function refreshFrame() {
      const url = `http://localhost:8080/api/v1/surveillance/simulated/${cameraId}/frame.jpg?t=${Date.now()}`;
      img.src = url;
    }
    
    // Refresh every second (1 FPS)
    setInterval(refreshFrame, 1000);
    refreshFrame();
  </script>
</body>
</html>
```

**React:**
```tsx
import { useState, useEffect } from 'react';

function CameraFeedPolling({ cameraId, token }: { cameraId: string; token: string }) {
  const [frameUrl, setFrameUrl] = useState('');
  
  useEffect(() => {
    const interval = setInterval(() => {
      setFrameUrl(
        `http://localhost:8080/api/v1/surveillance/simulated/${cameraId}/frame.jpg?t=${Date.now()}`
      );
    }, 1000); // 1 FPS
    
    return () => clearInterval(interval);
  }, [cameraId]);
  
  return (
    <div>
      <h2>Camera: {cameraId}</h2>
      <img 
        src={frameUrl} 
        alt="Camera feed"
        style={{ border: '2px solid #333' }}
      />
    </div>
  );
}
```

### Option B: WebSocket Streaming (10 FPS)

**Dependencies:**
```bash
npm install @stomp/stompjs sockjs-client
```

**React Component:**
```tsx
import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

function CameraFeedWebSocket({ cameraId }: { cameraId: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const clientRef = useRef<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [frameCount, setFrameCount] = useState(0);
  const [fps, setFps] = useState(0);
  
  useEffect(() => {
    // Create STOMP client
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/api/v1/ws'),
      
      onConnect: () => {
        console.log('WebSocket connected');
        setIsConnected(true);
        
        // Subscribe to camera frames
        client.subscribe(`/topic/camera/${cameraId}/frames`, (message) => {
          const frameData = JSON.parse(message.body);
          drawFrame(frameData);
          setFrameCount(frameData.frameNumber);
        });
        
        // Start streaming
        client.publish({
          destination: `/app/surveillance/stream/start/${cameraId}`,
        });
      },
      
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setIsConnected(false);
      },
      
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
    });
    
    client.activate();
    clientRef.current = client;
    
    // FPS counter
    let lastFrameCount = 0;
    const fpsInterval = setInterval(() => {
      setFps((prev) => {
        const currentFps = frameCount - lastFrameCount;
        lastFrameCount = frameCount;
        return currentFps;
      });
    }, 1000);
    
    // Cleanup
    return () => {
      if (clientRef.current) {
        clientRef.current.publish({
          destination: `/app/surveillance/stream/stop/${cameraId}`,
        });
        clientRef.current.deactivate();
      }
      clearInterval(fpsInterval);
    };
  }, [cameraId]);
  
  const drawFrame = (frameData: any) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    
    const img = new Image();
    img.onload = () => {
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    };
    img.src = `data:image/jpeg;base64,${frameData.data}`;
  };
  
  return (
    <div style={{ fontFamily: 'Arial, sans-serif' }}>
      <div style={{ marginBottom: '10px' }}>
        <h2>Camera: {cameraId}</h2>
        <div>
          Status: {isConnected ? '🟢 Connected' : '🔴 Disconnected'}
          {' | '}
          Frame: {frameCount}
          {' | '}
          FPS: {fps}
        </div>
      </div>
      <canvas 
        ref={canvasRef} 
        width={640} 
        height={480}
        style={{ border: '2px solid #333', borderRadius: '4px' }}
      />
    </div>
  );
}

export default CameraFeedWebSocket;
```

**Vanilla JavaScript (with SockJS & STOMP):**
```html
<!DOCTYPE html>
<html>
<head>
  <title>Camera Feed - WebSocket</title>
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
</head>
<body>
  <h1>Camera Feed (WebSocket)</h1>
  <div id="status">Status: Connecting...</div>
  <canvas id="camera-canvas" width="640" height="480" style="border: 2px solid #333;"></canvas>
  
  <script>
    const cameraId = 'camera-1';
    const canvas = document.getElementById('camera-canvas');
    const ctx = canvas.getContext('2d');
    const statusDiv = document.getElementById('status');
    
    // Create STOMP client
    const client = new StompJs.Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/api/v1/ws'),
      
      onConnect: () => {
        console.log('Connected');
        statusDiv.textContent = 'Status: 🟢 Connected';
        
        // Subscribe to frames
        client.subscribe(`/topic/camera/${cameraId}/frames`, (message) => {
          const frameData = JSON.parse(message.body);
          drawFrame(frameData);
        });
        
        // Start stream
        client.publish({
          destination: `/app/surveillance/stream/start/${cameraId}`,
        });
      },
      
      onDisconnect: () => {
        statusDiv.textContent = 'Status: 🔴 Disconnected';
      },
    });
    
    client.activate();
    
    function drawFrame(frameData) {
      const img = new Image();
      img.onload = () => {
        ctx.drawImage(img, 0, 0);
      };
      img.src = 'data:image/jpeg;base64,' + frameData.data;
    }
    
    // Stop stream on page unload
    window.addEventListener('beforeunload', () => {
      client.publish({
        destination: `/app/surveillance/stream/stop/${cameraId}`,
      });
    });
  </script>
</body>
</html>
```

### Option C: Multi-Camera Grid View

```tsx
import CameraFeedWebSocket from './CameraFeedWebSocket';

function CameraGrid() {
  const cameras = ['camera-1', 'camera-2', 'camera-3', 'camera-4'];
  
  return (
    <div style={{ 
      display: 'grid', 
      gridTemplateColumns: 'repeat(2, 1fr)', 
      gap: '20px',
      padding: '20px'
    }}>
      {cameras.map((cameraId) => (
        <CameraFeedWebSocket key={cameraId} cameraId={cameraId} />
      ))}
    </div>
  );
}
```

---

## Testing

### 1. Test Single Frame Endpoint

```bash
# Login first to get token
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"1234567890","password":"yourpassword"}' \
  | jq -r '.accessToken')

# Get frame
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/surveillance/simulated/camera-1/frame.jpg \
  -o test-frame.jpg

# Verify image
open test-frame.jpg  # macOS
# or
xdg-open test-frame.jpg  # Linux
```

### 2. Test WebSocket with wscat

```bash
# Install wscat
npm install -g wscat

# Connect to WebSocket
wscat -c ws://localhost:8080/api/v1/ws

# Once connected, send STOMP CONNECT frame
CONNECT
accept-version:1.2
heart-beat:10000,10000

^@

# Subscribe to camera feed
SUBSCRIBE
id:sub-0
destination:/topic/camera/camera-1/frames

^@

# Start stream
SEND
destination:/app/surveillance/stream/start/camera-1

^@

# You should see frame messages coming in
```

### 3. Test with Postman

1. Import WebSocket request: `ws://localhost:8080/api/v1/ws`
2. Send STOMP frames as shown above
3. Monitor incoming frame messages

### 4. Check Streaming Stats

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/surveillance/simulated/stats
```

Expected response:
```json
{
  "activeStreams": 1,
  "activeCameraIds": ["camera-1"]
}
```

---

## Frame Format Details

### Visual Elements

Each generated frame includes:

1. **Dynamic Background** - Color-shifting gradient (changes over time)
2. **Grid Pattern** - 50px grid overlay (simulates scene structure)
3. **Moving Object** - Red circle moving horizontally (simulates motion)
4. **Info Overlay** - Black semi-transparent box with:
   - Camera ID (truncated to 8 chars)
   - Current timestamp (yyyy-MM-dd HH:mm:ss)
   - Live status indicator (green dot)
   - Target FPS (10 FPS)
   - "SENTRIUM TEST FEED" watermark

### Technical Specs

- **Resolution:** 640x480 pixels
- **Format:** JPEG
- **Frame Rate:** 10 FPS (via WebSocket)
- **Encoding:** Base64 (in WebSocket messages)
- **Size:** ~15-25 KB per frame

---

## Performance Considerations

### Backend

- **Max concurrent streams:** 20 (configurable in AsyncConfig)
- **Thread pool:** 5 core, 20 max threads
- **Memory:** ~1 MB per active stream
- **CPU:** ~5-10% per stream on modern hardware

### Frontend

- **WebSocket:** More efficient than polling (10 FPS vs 1-2 FPS)
- **Polling:** Easier to implement, lower FPS
- **Canvas rendering:** Hardware accelerated
- **Memory:** ~50 MB per active stream in browser

### Network Bandwidth

- **WebSocket (10 FPS):** ~1.5-2.5 Mbps per stream
- **Polling (1 FPS):** ~150-250 Kbps per stream
- **Multi-camera:** Scales linearly (4 cameras = 4x bandwidth)

---

## Troubleshooting

### Issue: WebSocket not connecting

**Check:**
1. Backend is running on correct port (8080)
2. CORS is configured for your frontend origin
3. Browser console for error messages

**Fix:**
```bash
# Check WebSocket endpoint
curl -i http://localhost:8080/api/v1/ws/info

# Should return SockJS info
```

### Issue: Frames not rendering

**Check:**
1. Canvas element exists in DOM
2. Base64 data is valid JPEG
3. Browser console for image load errors

**Debug:**
```javascript
// Log frame data
client.subscribe('/topic/camera/camera-1/frames', (message) => {
  const frameData = JSON.parse(message.body);
  console.log('Frame received:', frameData.frameNumber);
  console.log('Data length:', frameData.data.length);
});
```

### Issue: Low FPS

**Possible causes:**
1. Backend overloaded (too many streams)
2. Network congestion
3. Slow canvas rendering

**Fix:**
```javascript
// Monitor frame rate
let lastTime = Date.now();
let frameCount = 0;

client.subscribe('/topic/camera/camera-1/frames', (message) => {
  frameCount++;
  const now = Date.now();
  if (now - lastTime >= 1000) {
    console.log('FPS:', frameCount);
    frameCount = 0;
    lastTime = now;
  }
});
```

### Issue: Memory leak

**Symptoms:** Browser/backend memory keeps growing

**Fix:**
- Always clean up WebSocket connections on component unmount
- Stop streams before disconnecting
- Clear canvas between frames

```tsx
useEffect(() => {
  return () => {
    // Proper cleanup
    client.publish({
      destination: `/app/surveillance/stream/stop/${cameraId}`,
    });
    client.deactivate();
  };
}, []);
```

---

## Next Steps

### Integrate with Real Cameras

Once you're ready to connect real cameras:

1. **Add stream URL fields to Device entity**
   ```sql
   ALTER TABLE device ADD COLUMN stream_url VARCHAR(500);
   ALTER TABLE device ADD COLUMN stream_type VARCHAR(20);
   ```

2. **Update endpoints to support real streams**
   ```java
   @GetMapping("/{cameraId}/stream")
   public ResponseEntity<StreamInfo> getStream(@PathVariable UUID id) {
       Device device = deviceService.findById(id);
       if (device.getStreamUrl() != null) {
           // Return real stream
           return realStreamService.getStreamInfo(device);
       } else {
           // Fall back to simulated
           return simulatedStreamService.getStreamInfo(device);
       }
   }
   ```

3. **Proxy RTSP streams**
   - Use FFmpeg to convert RTSP → HLS
   - Store segments on disk or S3
   - Serve via HTTP

### Record Video Clips

1. Trigger recording on motion events
2. Use FFmpeg to capture 30s clips
3. Store in `video_clip` table
4. Implement retention policy

---

## Security Notes

⚠️ **Current Configuration:**
- WebSocket endpoint (`/ws/**`) is publicly accessible (no auth required)
- This is intentional for demo/development purposes

🔒 **For Production:**
1. Add JWT token validation to WebSocket handshake
2. Implement per-user stream quotas
3. Add rate limiting to prevent DoS
4. Use WSS (WebSocket Secure) with TLS
5. Validate camera IDs against user permissions

**Example secure WebSocket config:**
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            // Validate JWT token from STOMP headers
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            String token = accessor.getFirstNativeHeader("Authorization");
            // Validate token...
            return message;
        }
    });
}
```

---

**Ready to test!** Start your backend and try the examples above.
