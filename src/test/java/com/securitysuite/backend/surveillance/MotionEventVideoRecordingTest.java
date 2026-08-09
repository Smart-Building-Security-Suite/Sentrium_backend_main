package com.securitysuite.backend.surveillance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.surveillance.dto.CreateMotionEventRequest;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.device.DeviceType;
import com.securitysuite.backend.device.DeviceStatus;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import com.securitysuite.backend.videoclip.VideoClipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for verifying that video clips are recorded when motion events are detected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MotionEventVideoRecordingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private MotionEventRepository motionEventRepository;

    @Autowired
    private VideoClipRepository videoClipRepository;

    private Zone testZone;
    private Device testCameraWithStream;
    private Device testCameraWithoutStream;

    @BeforeEach
    void setUp() {
        motionEventRepository.deleteAll();
        videoClipRepository.deleteAll();
        deviceRepository.deleteAll();
        zoneRepository.deleteAll();

        // Create test zone
        testZone = new Zone();
        testZone.setName("Test Zone");
        testZone.setLocationDescription("Test Location");
        testZone.setActive(true);
        testZone = zoneRepository.save(testZone);

        // Create camera WITH stream URL (should trigger video recording)
        testCameraWithStream = new Device();
        testCameraWithStream.setName("Test Camera With Stream");
        testCameraWithStream.setType(DeviceType.CAMERA);
        testCameraWithStream.setZone(testZone);
        testCameraWithStream.setStatus(DeviceStatus.IDLE);
        testCameraWithStream.setStreamUrl("rtsp://example.com/stream");
        testCameraWithStream.setStreamResolution("1080p");
        testCameraWithStream.setStreamType("RTSP");
        testCameraWithStream = deviceRepository.save(testCameraWithStream);

        // Create camera WITHOUT stream URL (should use simulated clip)
        testCameraWithoutStream = new Device();
        testCameraWithoutStream.setName("Test Camera Without Stream");
        testCameraWithoutStream.setType(DeviceType.CAMERA);
        testCameraWithoutStream.setZone(testZone);
        testCameraWithoutStream.setStatus(DeviceStatus.IDLE);
        testCameraWithoutStream.setStreamResolution("720p");
        testCameraWithoutStream = deviceRepository.save(testCameraWithoutStream);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMotionEventTriggersVideoClipRecording() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                testCameraWithStream.getId().toString(),
                0.90
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Wait for async recording to complete
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .pollInterval(java.time.Duration.ofMillis(500))
                .until(() -> videoClipRepository.findByCameraIdOrderByStartTimeDesc(
                        testCameraWithStream.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)
                ).getContent().size() > 0);

        // Verify clip was created
        assert videoClipRepository.findByCameraIdOrderByStartTimeDesc(
                testCameraWithStream.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
        ).getContent().size() == 1;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMotionEventWithoutStreamCreatesSimulatedClip() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                testCameraWithoutStream.getId().toString(),
                0.85
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Wait for simulated clip creation
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .pollInterval(java.time.Duration.ofMillis(500))
                .until(() -> videoClipRepository.findByCameraIdOrderByStartTimeDesc(
                        testCameraWithoutStream.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)
                ).getContent().size() > 0);

        // Verify simulated clip was created
        var clips = videoClipRepository.findByCameraIdOrderByStartTimeDesc(
                testCameraWithoutStream.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
        ).getContent();

        assert clips.size() == 1;
        assert clips.get(0).getTriggerType().equals("MOTION");
        assert clips.get(0).getFileSizeBytes() == 5242880L; // Simulated 5MB
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMultipleMotionEventsCreateMultipleClips() throws Exception {
        for (int i = 0; i < 3; i++) {
            CreateMotionEventRequest request = new CreateMotionEventRequest(
                    testCameraWithoutStream.getId().toString(),
                    0.75 + (i * 0.05)
            );

            mockMvc.perform(post("/surveillance/motion-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            Thread.sleep(100);
        }

        // Wait for all clips to be created
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> videoClipRepository.findByCameraIdOrderByStartTimeDesc(
                        testCameraWithoutStream.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)
                ).getContent().size() == 3);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testVideoClipMetadataIsCorrect() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                testCameraWithoutStream.getId().toString(),
                0.88
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Wait for clip creation
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> videoClipRepository.count() > 0);

        var clip = videoClipRepository.findAll().iterator().next();

        assert clip.getCamera().getId().equals(testCameraWithoutStream.getId());
        assert clip.getTriggerType().equals("MOTION");
        assert clip.getFormat().equals("MP4");
        assert clip.getResolution().equals("720p");
        assert clip.getDurationSeconds() == 30;
        assert clip.getArchived() == false;
        assert clip.getRetentionUntil() != null;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testVideoClipCanBeListedViaController() throws Exception {
        // Create motion event to trigger clip recording
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                testCameraWithoutStream.getId().toString(),
                0.85
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Wait for clip
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .until(() -> videoClipRepository.count() > 0);

        // List clips via API
        mockMvc.perform(get("/video-clips")
                .param("cameraId", testCameraWithoutStream.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].cameraId", is(testCameraWithoutStream.getId().toString())))
                .andExpect(jsonPath("$.content[0].triggerType", is("MOTION")))
                .andExpect(jsonPath("$.content[0].format", is("MP4")));
    }
}
