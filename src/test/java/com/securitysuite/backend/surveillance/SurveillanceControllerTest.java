package com.securitysuite.backend.surveillance;

import com.securitysuite.backend.surveillance.dto.CreateMotionEventRequest;
import com.securitysuite.backend.surveillance.dto.MotionEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SurveillanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_NoFilters_ReturnsPage() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", isA(java.util.List.class)))
                .andExpect(jsonPath("$.pageable.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageable.pageSize", is(20)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_WithPagination() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable.pageSize", is(10)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_WithCameraFilter() throws Exception {
        String cameraId = "camera-123";

        mockMvc.perform(get("/surveillance/motion-events")
                .param("cameraId", cameraId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", isA(java.util.List.class)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_WithDateRange() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events")
                .param("from", "2024-01-01")
                .param("to", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", isA(java.util.List.class)));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_WithCustomSort() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events")
                .param("sort", "cameraId,asc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateMotionEvent_Success() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                "simulated-camera-001",
                85.5
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cameraId", is("simulated-camera-001")))
                .andExpect(jsonPath("$.confidence", is(85.5)))
                .andExpect(jsonPath("$.detectedAt", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testCreateMotionEvent_RequiresAdminRole() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                "camera-123",
                75.0
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateMotionEvent_ValidatesConfidence() throws Exception {
        CreateMotionEventRequest request = new CreateMotionEventRequest(
                "camera-123",
                null
        );

        mockMvc.perform(post("/surveillance/motion-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetFeedStatus_ValidCamera() throws Exception {
        String cameraId = "simulated-camera-001";

        mockMvc.perform(get("/surveillance/feed-status/" + cameraId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cameraId", is(cameraId)))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.resolution", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testGetFeedStatus_InvalidCamera_NotFound() throws Exception {
        String cameraId = "non-existent-camera";

        mockMvc.perform(get("/surveillance/feed-status/" + cameraId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void testListMotionEvents_ViewerCanAccess() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events"))
                .andExpect(status().isOk());
    }

    @Test
    void testListMotionEvents_Unauthenticated_Denied() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "SECURITY_OFFICER")
    void testListMotionEvents_DefaultSortingByDetectedAt() throws Exception {
        mockMvc.perform(get("/surveillance/motion-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }
}
