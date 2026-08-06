package com.securitysuite.backend.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.auth.RevokedTokenRepository;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;
import com.securitysuite.backend.config.SecurityConfig;
import com.securitysuite.backend.security.JwtAuthFilter;
import com.securitysuite.backend.security.AuthRateLimitFilter;
import com.securitysuite.backend.security.RestAuthenticationEntryPoint;
import com.securitysuite.backend.security.RestAccessDeniedHandler;

@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, AuthRateLimitFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    @DisplayName("POST /devices - SECURITY_OFFICER can create device")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void createDevice_asOfficer_returns201() throws Exception {
        UUID zoneId = UUID.randomUUID();
        DeviceController.DeviceRequest req = new DeviceController.DeviceRequest("Main Gate Cam", DeviceType.CAMERA_SIM, zoneId);

        DeviceDto dto = new DeviceDto(UUID.randomUUID(), "Main Gate Cam", DeviceType.CAMERA_SIM, DeviceStatus.IDLE, zoneId, "Zone A", true, null, null, "HTTP", "DISCONNECTED", null, null, null, null, null);
        given(deviceService.create(any(), any(), any())).willReturn(dto);

        mockMvc.perform(post("/devices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Gate Cam"))
                .andExpect(jsonPath("$.type").value("CAMERA_SIM"))
                .andExpect(jsonPath("$.zoneName").value("Zone A"));
    }

    @Test
    @DisplayName("POST /devices - anonymous returns 401")
    void createDevice_anonymous_returns401() throws Exception {
        DeviceController.DeviceRequest req = new DeviceController.DeviceRequest("Cam", DeviceType.CAMERA_SIM, UUID.randomUUID());

        mockMvc.perform(post("/devices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /devices/{id}/heartbeat - SECURITY_OFFICER updates status")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void heartbeat_asOfficer_returns204() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceController.HeartbeatRequest req = new DeviceController.HeartbeatRequest(DeviceStatus.ONLINE);

        mockMvc.perform(post("/devices/{id}/heartbeat", deviceId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(deviceService).updateStatus(deviceId, DeviceStatus.ONLINE);
    }

    @Test
    @DisplayName("POST /devices/{id}/heartbeat - anonymous returns 401")
    void heartbeat_anonymous_returns401() throws Exception {
        DeviceController.HeartbeatRequest req = new DeviceController.HeartbeatRequest(DeviceStatus.ONLINE);

        mockMvc.perform(post("/devices/{id}/heartbeat", UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
