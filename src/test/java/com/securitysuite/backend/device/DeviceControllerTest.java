package com.securitysuite.backend.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private ZoneRepository zoneRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /devices - Create new device")
    void createDevice() throws Exception {
        UUID zoneId = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setId(zoneId);

        DeviceController.DeviceRequest req = new DeviceController.DeviceRequest("Main Gate Cam", DeviceType.CAMERA_SIM, zoneId);

        Device saved = new Device();
        saved.setId(UUID.randomUUID());
        saved.setName("Main Gate Cam");
        saved.setType(DeviceType.CAMERA_SIM);
        saved.setZone(zone);
        saved.setStatus(DeviceStatus.ONLINE);

        given(zoneRepository.findById(zoneId)).willReturn(Optional.of(zone));
        given(deviceRepository.save(any(Device.class))).willReturn(saved);

        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Gate Cam"))
                .andExpect(jsonPath("$.type").value("CAMERA_SIM"));
    }

    @Test
    @DisplayName("POST /devices/{id}/heartbeat - Update device status to ONLINE")
    void heartbeat() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Device device = new Device();
        device.setId(deviceId);
        device.setStatus(DeviceStatus.IDLE);

        given(deviceRepository.findById(deviceId)).willReturn(Optional.of(device));

        DeviceController.HeartbeatRequest req = new DeviceController.HeartbeatRequest(DeviceStatus.ONLINE);

        mockMvc.perform(post("/devices/" + deviceId + "/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(deviceRepository).save(device);
    }
}
