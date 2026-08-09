package com.securitysuite.backend.surveillance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.surveillance.dto.CreateMotionEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for surveillance system with simulated cameras.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SurveillanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MotionEventRepository motionEventRepository;

    private static final String CAMERA_ID = "integration-test-camera-001";
    private static final String SIMULATED_ENDPOINT = "/surveillance/simulated";
    private static final String SURVEILLANCE_ENDPOINT = "/surveillance";

    @BeforeEach
    void setUp() {
        motionEventRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testCompleteSimulatedFeedWorkflow() throws Exception {
        // Step 1: Get initial simulated frame
        byte[] frame1 = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + CAMERA_ID + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assert frame1.length > 1000 : "Frame should be valid JPEG with reasonable size";

        // Step 2: Get another frame (should be different due to animated content)
        Thread.sleep(100);
        byte[] frame2 = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + CAMERA_ID + "/frame.jpg"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assert frame2.length > 1000 : "Second frame should also be valid";

        // Step 3: Record a motion event for this simulated camera
        CreateMotionEventRequest motionRequest = new CreateMotionEventRequest(CAMERA_ID, 0.92);

        String motionResponse = mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(motionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cameraId", is(CAMERA_ID)))
                .andExpect(jsonPath("$.confidence", is(0.92)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assert motionResponse.contains(CAMERA_ID) : "Response should contain camera ID";

        // Step 4: List motion events and verify the created event appears
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("cameraId", CAMERA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].cameraId", is(CAMERA_ID)));

        // Step 5: Get feed status for the simulated camera
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/feed-status/" + CAMERA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cameraId", is(CAMERA_ID)))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.resolution", is("1080p")));

        // Step 6: Get streaming stats
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStreams", isA(Integer.class)))
                .andExpect(jsonPath("$.activeCameraIds", isA(java.util.List.class)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testMultipleSimulatedCameras() throws Exception {
        String[] cameraIds = {"camera-01", "camera-02", "camera-03"};

        // Get frames from multiple simulated cameras
        for (String cameraId : cameraIds) {
            byte[] frame = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + cameraId + "/frame.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            assert frame.length > 1000 : "Frame for " + cameraId + " should be valid";
        }

        // Record motion events from each camera
        for (String cameraId : cameraIds) {
            CreateMotionEventRequest request = new CreateMotionEventRequest(cameraId, 0.80 + (Math.random() * 0.15));

            mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Verify all motion events were recorded
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMotionEventCreationWithVariousConfidenceLevels() throws Exception {
        double[] confidenceLevels = {0.0, 0.25, 0.50, 0.75, 1.0};

        for (double confidence : confidenceLevels) {
            CreateMotionEventRequest request = new CreateMotionEventRequest(CAMERA_ID, confidence);

            mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.confidence", is(confidence)));
        }

        // Verify all events were stored
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("cameraId", CAMERA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testSimulatedFrameConsistency() throws Exception {
        // Get 5 frames in quick succession and verify they're all valid
        for (int i = 0; i < 5; i++) {
            byte[] frame = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + CAMERA_ID + "/frame.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            // Verify JPEG structure
            assert frame[0] == (byte) 0xFF : "Frame " + i + " should start with JPEG magic";
            assert frame[1] == (byte) 0xD8 : "Frame " + i + " should have valid JPEG marker";
            assert frame.length > 5000 : "Frame " + i + " should have reasonable size";
        }
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testMotionEventFiltering() throws Exception {
        // Create events for different cameras
        for (int i = 0; i < 3; i++) {
            CreateMotionEventRequest request = new CreateMotionEventRequest(
                    "camera-filter-test-" + i,
                    0.85 + (i * 0.05)
            );

            mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Filter by specific camera
        String specificCamera = "camera-filter-test-1";
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("cameraId", specificCamera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].cameraId", is(specificCamera)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testMotionEventPagination() throws Exception {
        // Create 25 motion events
        for (int i = 0; i < 25; i++) {
            CreateMotionEventRequest request = new CreateMotionEventRequest(CAMERA_ID, 0.80);

            mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Request first page (default size is 20)
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("cameraId", CAMERA_ID)
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.pageable.pageSize", is(20)))
                .andExpect(jsonPath("$.totalElements", is(25)));

        // Request second page
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("cameraId", CAMERA_ID)
                .param("page", "1")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements", is(25)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testSimulatedFramePerformance() throws Exception {
        long startTime = System.currentTimeMillis();

        // Generate 10 frames consecutively
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + CAMERA_ID + "/frame.jpg"))
                    .andExpect(status().isOk());
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        long avgFrameTime = elapsedTime / 10;

        // Should be reasonably fast (less than 200ms per frame on average)
        assert avgFrameTime < 200 : "Average frame generation time should be < 200ms, was " + avgFrameTime + "ms";
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testInvalidMotionEventConfidence() throws Exception {
        // Test below minimum (0.0)
        CreateMotionEventRequest invalidLow = new CreateMotionEventRequest(CAMERA_ID, -0.1);
        mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidLow)))
                .andExpect(status().isBadRequest());

        // Test above maximum (1.0)
        CreateMotionEventRequest invalidHigh = new CreateMotionEventRequest(CAMERA_ID, 1.1);
        mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidHigh)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testDateRangeFiltering() throws Exception {
        // Create a motion event
        CreateMotionEventRequest request = new CreateMotionEventRequest(CAMERA_ID, 0.85);
        mockMvc.perform(post(SURVEILLANCE_ENDPOINT + "/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Filter by today's date
        LocalDate today = LocalDate.now();
        mockMvc.perform(get(SURVEILLANCE_ENDPOINT + "/motion-events")
                .param("from", today.toString())
                .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testStopSimulatedStream() throws Exception {
        // Stop a stream that may or may not be active (should always succeed)
        mockMvc.perform(post(SIMULATED_ENDPOINT + "/" + CAMERA_ID + "/stop"))
                .andExpect(status().isOk());
    }
}
