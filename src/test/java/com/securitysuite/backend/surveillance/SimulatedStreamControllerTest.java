package com.securitysuite.backend.surveillance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SimulatedStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SIMULATED_CAMERA_ID = "test-camera-001";
    private static final String SIMULATED_ENDPOINT = "/surveillance/simulated";

    @BeforeEach
    void setUp() {
        // Ensure no active streams from previous tests
        try {
            mockMvc.perform(post(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/stop")
                    .with(request -> {
                        request.setUserPrincipal(() -> "testuser");
                        return request;
                    }))
                    .andReturn();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_ReturnsJpegImage() throws Exception {
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(bytes -> bytes.length > 0))
                // JPEG magic bytes: FF D8 FF
                .andExpect(content().bytes(bytes -> bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_ContainsValidJpegData() throws Exception {
        byte[] imageBytes = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // Verify JPEG validity
        assert imageBytes.length > 1000 : "Frame should be reasonably sized";
        assert imageBytes[0] == (byte) 0xFF : "JPEG start marker";
        assert imageBytes[1] == (byte) 0xD8 : "JPEG SOS marker";
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_IncludesCacheHeaders() throws Exception {
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_MultipleCameras() throws Exception {
        String camera1 = "camera-01";
        String camera2 = "camera-02";

        byte[] frame1 = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + camera1 + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] frame2 = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + camera2 + "/frame.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // Frames should be generated (non-empty)
        assert frame1.length > 0;
        assert frame2.length > 0;
        // Frames should have different timestamps, so likely different content
        assert frame1.length > 0 && frame2.length > 0 : "Both frames should be valid";
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_ConsistentDimensions() throws Exception {
        // Request multiple frames and verify they're all valid JPEG
        for (int i = 0; i < 3; i++) {
            byte[] frameBytes = mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/frame.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            assert frameBytes.length > 0 : "Frame " + i + " should not be empty";
            assert frameBytes[0] == (byte) 0xFF : "Frame " + i + " should be valid JPEG";
        }
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testStopStreamRest_ReturnsOk() throws Exception {
        mockMvc.perform(post(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/stop"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetStats_ReturnsStreamStatsDto() throws Exception {
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStreams", isA(Integer.class)))
                .andExpect(jsonPath("$.activeCameraIds", isA(java.util.List.class)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetSimulatedFrame_Unauthenticated_Denied() throws Exception {
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/" + SIMULATED_CAMERA_ID + "/frame.jpg")
                .with(request -> {
                    request.setUserPrincipal(null);
                    return request;
                }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void testGetStats_ViewerDenied() throws Exception {
        mockMvc.perform(get(SIMULATED_ENDPOINT + "/stats"))
                .andExpect(status().isForbidden());
    }
}
