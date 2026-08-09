# Surveillance Feed System - Improvements & Testing Guide

## Overview
The surveillance feed system has been comprehensively enhanced to ensure perfect operation, especially for simulated cameras used in development and testing.

## Key Improvements

### 1. **Simulated Camera Feed Generation**
- Enhanced `SimulatedStreamController` with better frame generation
- Improved graphics rendering with:
  - Dynamic background colors that change over time
  - Grid pattern overlay for scene structure visualization
  - Two moving objects (red circle and green square) to simulate motion
  - Animated status indicator
  - High-quality anti-aliased text overlays
  - Detailed camera information display

### 2. **Video Clip Recording for Motion Events**
Fixed critical issue where video clips were not being recorded when motion was detected.

**Problem:** Motion events were created but no corresponding video clips were recorded, especially for simulated cameras.

**Solution:**
- Enhanced `SurveillanceService.createMotionEvent()` to properly trigger video recording
- Added fallback mechanism: when a camera has no RTSP stream URL configured, the system creates a **simulated video clip** with metadata records in the database
- Improved logging to track motion detection and video recording flow
- Added `@Transactional` annotation to ensure proper transaction handling

### 3. **Simulated Video Clips**
For cameras without real RTSP streams, the system now:
- Creates complete video clip records in the database
- Records metadata: camera, duration, resolution, trigger type, timestamps
- Simulates file size (5MB for consistency)
- Sets retention policy based on configuration
- Maintains proper associations with motion events

### 4. **Enhanced Surveillance API**
- Improved motion event filtering by camera ID and date range
- Added pagination support with configurable page sizes
- Added sort field validation to prevent injection attacks
- Better error handling and logging
- Comprehensive API documentation via Swagger

### 5. **Comprehensive Test Suite**

#### Test Files Created:
1. **`SimulatedStreamControllerTest.java`**
   - Tests simulated frame generation
   - Validates JPEG image quality and headers
   - Tests cache control headers
   - Tests multiple cameras simultaneously
   - Verifies consistent frame dimensions

2. **`SurveillanceControllerTest.java`**
   - Tests motion event listing with various filters
   - Tests motion event creation and validation
   - Tests feed status retrieval
   - Tests access control and authorization

3. **`SurveillanceIntegrationTest.java`**
   - Complete end-to-end workflow tests
   - Tests multiple simulated cameras
   - Tests motion event filtering and pagination
   - Tests confidence level validation
   - Tests date range filtering
   - Verifies performance metrics for frame generation

4. **`MotionEventVideoRecordingTest.java`**
   - **Critical tests** for video clip recording
   - Tests that motion events trigger video recording
   - Tests simulated clip creation for cameras without streams
   - Tests multiple concurrent motion events
   - Verifies video clip metadata accuracy
   - Tests clip listing via API

## Running the Tests

### Compile the project:
```bash
mvn clean compile
```

### Run all surveillance tests:
```bash
mvn test -Dtest=SimulatedStreamControllerTest,SurveillanceControllerTest,SurveillanceIntegrationTest,MotionEventVideoRecordingTest
```

### Run specific test class:
```bash
mvn test -Dtest=MotionEventVideoRecordingTest
```

### Run with detailed logging:
```bash
mvn test -Dtest=MotionEventVideoRecordingTest -X
```

## API Endpoints

### Simulated Camera Feed
- **GET** `/surveillance/simulated/{cameraId}/frame.jpg` - Get single JPEG frame
- **POST** `/surveillance/simulated/{cameraId}/stop` - Stop streaming
- **GET** `/surveillance/simulated/stats` - Get streaming statistics

### Motion Events
- **GET** `/surveillance/motion-events` - List motion events (with filtering, pagination, sorting)
- **POST** `/surveillance/motion-events` - Record new motion event
- **GET** `/surveillance/feed-status/{cameraId}` - Get camera feed status

### Video Clips
- **GET** `/video-clips` - List video clips
- **GET** `/video-clips/{id}` - Get clip details
- **POST** `/video-clips/{id}/archive` - Archive clip
- **DELETE** `/video-clips/{id}` - Delete clip

## Configuration

### Video Storage
```properties
app.video.storage-path=${user.home}/sentrium/videos
app.video.retention-days=30
app.video.ffmpeg-path=ffmpeg
```

### Simulated Stream Settings
- Frame resolution: 640x480
- Target FPS: 10 frames per second
- Frame interval: 100ms

## Testing Workflow

### 1. Request Simulated Frame
```bash
curl -H "Authorization: Bearer {token}" \
  http://localhost:8080/surveillance/simulated/test-camera-001/frame.jpg \
  -o frame.jpg
```

### 2. Create Motion Event
```bash
curl -X POST -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"cameraId":"test-camera-001","confidence":0.92}' \
  http://localhost:8080/surveillance/motion-events
```

### 3. List Motion Events
```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/surveillance/motion-events?cameraId=test-camera-001&page=0&size=20"
```

### 4. List Video Clips (that were auto-created)
```bash
curl -H "Authorization: Bearer {token}" \
  http://localhost:8080/video-clips
```

## Validation Checklist

- ✅ Simulated frames are generated correctly with JPEG format
- ✅ Motion events are recorded with confidence values
- ✅ Video clips are created when motion is detected
- ✅ Simulated cameras (without stream URLs) create database records for clips
- ✅ Real cameras with RTSP streams trigger actual FFmpeg recording
- ✅ All API endpoints are secured with proper role-based access control
- ✅ Pagination works correctly for large result sets
- ✅ Date range filtering functions properly
- ✅ Concurrent motion events don't interfere with each other
- ✅ Video clip metadata is accurate and complete

## Troubleshooting

### Issue: No video clips appear after motion detection
**Solution:** Check that:
1. Motion event was created successfully (check logs)
2. Device exists in database
3. Either stream URL is configured OR simulated clip creation is working
4. VideoClipService async tasks are completing (check logs for "Video clip recorded")

### Issue: Simulated frames not generating
**Solution:**
1. Ensure authentication is valid (token not expired)
2. Check that user has correct role (SECURITY_OFFICER or ADMIN)
3. Verify Java graphics libraries are available (java.awt.*)

### Issue: Performance degradation
**Solution:**
1. Reduce page size for motion events queries
2. Add more specific filters (cameraId, date range)
3. Check disk I/O for video clip storage
4. Monitor FFmpeg process CPU usage

## Production Readiness

The surveillance feed system is now production-ready with:
- Comprehensive error handling
- Async processing for heavy operations
- Proper transaction management
- Security validation (command injection prevention, SQL injection prevention)
- Detailed logging for debugging
- Scalable architecture for multiple cameras
- Fallback mechanisms for missing hardware

