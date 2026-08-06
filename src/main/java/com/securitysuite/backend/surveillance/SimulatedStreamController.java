package com.securitysuite.backend.surveillance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulated camera feed controller for development and testing.
 * Generates test pattern frames without requiring real camera hardware.
 */
@RestController
@RequestMapping("/surveillance/simulated")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Surveillance - Simulated", description = "Simulated camera feeds for development and testing")
public class SimulatedStreamController {

    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, AtomicBoolean> activeStreams = new ConcurrentHashMap<>();

    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 480;
    private static final int TARGET_FPS = 10; // 10 frames per second for demo
    private static final long FRAME_INTERVAL_MS = 1000 / TARGET_FPS;

    /**
     * Get a single simulated frame as JPEG.
     * This endpoint can be used for MJPEG-style streaming by repeatedly requesting frames.
     */
    @GetMapping("/{cameraId}/frame.jpg")
    @Operation(summary = "Get simulated camera frame",
               description = "Returns a single frame with test pattern and camera information overlay. Useful for MJPEG-style streaming by polling this endpoint.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getSimulatedFrame(@PathVariable String cameraId) {
        try {
            byte[] imageBytes = generateFrame(cameraId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
        } catch (IOException e) {
            log.error("Failed to generate simulated frame for camera {}", cameraId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Start streaming simulated frames via WebSocket.
     * Client subscribes to /topic/camera/{cameraId}/frames to receive base64-encoded frames.
     */
    @MessageMapping("/surveillance/stream/start/{cameraId}")
    public void startStream(@DestinationVariable String cameraId) {
        String streamKey = cameraId;

        // Check if stream is already active
        AtomicBoolean isActive = activeStreams.computeIfAbsent(streamKey, k -> new AtomicBoolean(false));

        if (isActive.compareAndSet(false, true)) {
            log.info("Starting simulated stream for camera: {}", cameraId);
            streamFrames(cameraId);
        } else {
            log.info("Stream already active for camera: {}", cameraId);
        }
    }

    /**
     * Stop streaming frames for a specific camera.
     */
    @MessageMapping("/surveillance/stream/stop/{cameraId}")
    public void stopStream(@DestinationVariable String cameraId) {
        AtomicBoolean isActive = activeStreams.get(cameraId);
        if (isActive != null) {
            isActive.set(false);
            activeStreams.remove(cameraId);
            log.info("Stopped simulated stream for camera: {}", cameraId);
        }
    }

    /**
     * REST endpoint to stop a stream.
     */
    @PostMapping("/{cameraId}/stop")
    @Operation(summary = "Stop simulated camera stream",
               description = "Stops the WebSocket stream for a specific camera.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> stopStreamRest(@PathVariable String cameraId) {
        stopStream(cameraId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get streaming statistics.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get streaming statistics",
               description = "Returns the number of active simulated camera streams.")
    @PreAuthorize("hasAnyRole('ADMIN','SECURITY_OFFICER')")
    public ResponseEntity<StreamStatsDto> getStats() {
        long activeCount = activeStreams.values().stream()
                .filter(AtomicBoolean::get)
                .count();

        return ResponseEntity.ok(new StreamStatsDto(
                (int) activeCount,
                activeStreams.keySet().stream().toList()
        ));
    }

    // -------------------------------------------------------------------------
    // Private methods
    // -------------------------------------------------------------------------

    @Async
    protected void streamFrames(String cameraId) {
        AtomicBoolean isActive = activeStreams.get(cameraId);
        int frameCount = 0;

        try {
            while (isActive != null && isActive.get()) {
                long startTime = System.currentTimeMillis();

                // Generate and send frame
                byte[] frameBytes = generateFrame(cameraId);
                String base64Frame = Base64.getEncoder().encodeToString(frameBytes);

                FrameMessage message = new FrameMessage(
                        cameraId,
                        frameCount++,
                        System.currentTimeMillis(),
                        base64Frame,
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                );

                messagingTemplate.convertAndSend(
                        "/topic/camera/" + cameraId + "/frames",
                        message
                );

                // Calculate sleep time to maintain target FPS
                long elapsedTime = System.currentTimeMillis() - startTime;
                long sleepTime = Math.max(0, FRAME_INTERVAL_MS - elapsedTime);

                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        } catch (InterruptedException e) {
            log.warn("Stream interrupted for camera {}", cameraId);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("Error generating frame for camera {}", cameraId, e);
        } finally {
            activeStreams.remove(cameraId);
            log.info("Stream ended for camera: {}", cameraId);
        }
    }

    private byte[] generateFrame(String cameraId) throws IOException {
        BufferedImage image = new BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing for better text rendering
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Generate dynamic background (simulates changing scene)
        int colorVariation = (int) ((System.currentTimeMillis() / 100) % 255);
        Color backgroundColor = new Color(
                (colorVariation + 50) % 256,
                (colorVariation + 100) % 256,
                (colorVariation + 150) % 256
        );
        g.setColor(backgroundColor);
        g.fillRect(0, 0, FRAME_WIDTH, FRAME_HEIGHT);

        // Draw grid pattern (simulates scene structure)
        g.setColor(new Color(255, 255, 255, 30));
        for (int x = 0; x < FRAME_WIDTH; x += 50) {
            g.drawLine(x, 0, x, FRAME_HEIGHT);
        }
        for (int y = 0; y < FRAME_HEIGHT; y += 50) {
            g.drawLine(0, y, FRAME_WIDTH, y);
        }

        // Draw moving "object" (simulates motion detection)
        int objectX = (int) ((System.currentTimeMillis() / 50) % FRAME_WIDTH);
        int objectY = FRAME_HEIGHT / 2 + (int) (Math.sin(System.currentTimeMillis() / 500.0) * 100);
        g.setColor(new Color(255, 0, 0, 180));
        g.fillOval(objectX - 15, objectY - 15, 30, 30);

        // Draw camera info overlay
        drawOverlay(g, cameraId);

        g.dispose();

        // Convert to JPEG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private void drawOverlay(Graphics2D g, String cameraId) {
        // Semi-transparent overlay background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(10, 10, FRAME_WIDTH - 20, 120);

        // Camera ID
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        String displayId = cameraId.length() > 8 ? cameraId.substring(0, 8) : cameraId;
        g.drawString("Camera: " + displayId, 20, 35);

        // Timestamp
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        g.drawString("Time: " + timestamp, 20, 60);

        // Status indicator
        g.setColor(Color.GREEN);
        g.fillOval(20, 75, 15, 15);
        g.setColor(Color.WHITE);
        g.drawString("LIVE - SIMULATED", 45, 88);

        // FPS indicator
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.drawString("Target: " + TARGET_FPS + " FPS", 20, 110);

        // Watermark
        g.setColor(new Color(255, 255, 255, 100));
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString("SENTRIUM TEST FEED", FRAME_WIDTH - 180, FRAME_HEIGHT - 20);
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public record FrameMessage(
            String cameraId,
            int frameNumber,
            long timestamp,
            String data,  // Base64 encoded JPEG
            int width,
            int height
    ) {}

    public record StreamStatsDto(
            int activeStreams,
            java.util.List<String> activeCameraIds
    ) {}
}
