package com.securitysuite.backend.zone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZoneController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ZoneRepository zoneRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /zones - Returns list of zones")
    void listZones() throws Exception {
        Zone z1 = new Zone();
        z1.setId(UUID.randomUUID());
        z1.setName("North Wing");
        z1.setFloor("Floor 1");
        z1.setBuilding("HQ");

        given(zoneRepository.findAll()).willReturn(List.of(z1));

        mockMvc.perform(get("/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("North Wing"))
                .andExpect(jsonPath("$[0].building").value("HQ"));
    }

    @Test
    @DisplayName("POST /zones - Create zone successfully")
    void createZone() throws Exception {
        ZoneController.ZoneRequest req = new ZoneController.ZoneRequest("South Wing", "Floor 2", "HQ");

        Zone saved = new Zone();
        saved.setId(UUID.randomUUID());
        saved.setName("South Wing");
        saved.setFloor("Floor 2");
        saved.setBuilding("HQ");

        given(zoneRepository.save(any(Zone.class))).willReturn(saved);

        mockMvc.perform(post("/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("South Wing"));
    }
}
