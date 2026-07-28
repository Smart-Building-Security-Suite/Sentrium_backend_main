package com.securitysuite.backend.zone;

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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;
import com.securitysuite.backend.config.SecurityConfig;
import com.securitysuite.backend.security.JwtAuthFilter;
import com.securitysuite.backend.security.AuthRateLimitFilter;
import com.securitysuite.backend.security.RestAuthenticationEntryPoint;
import com.securitysuite.backend.security.RestAccessDeniedHandler;

@WebMvcTest(ZoneController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, AuthRateLimitFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ZoneService zoneService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    @DisplayName("GET /zones - authenticated user gets list of zones")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void listZones() throws Exception {
        ZoneDto z1 = new ZoneDto(UUID.randomUUID(), "North Wing", "Floor 1", "HQ");
        given(zoneService.listAll()).willReturn(List.of(z1));

        mockMvc.perform(get("/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("North Wing"))
                .andExpect(jsonPath("$[0].building").value("HQ"));
    }

    @Test
    @DisplayName("GET /zones - anonymous returns 401")
    void listZones_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/zones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /zones - ADMIN can create zone")
    @WithMockUser(roles = "ADMIN")
    void createZone_asAdmin_returns201() throws Exception {
        ZoneController.ZoneRequest req = new ZoneController.ZoneRequest("South Wing", "Floor 2", "HQ");
        ZoneDto saved = new ZoneDto(UUID.randomUUID(), "South Wing", "Floor 2", "HQ");

        given(zoneService.create(eq("South Wing"), eq("Floor 2"), eq("HQ"))).willReturn(saved);

        mockMvc.perform(post("/zones").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("South Wing"));
    }

    @Test
    @DisplayName("POST /zones - SECURITY_OFFICER is forbidden (403)")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void createZone_asOfficer_returns403() throws Exception {
        ZoneController.ZoneRequest req = new ZoneController.ZoneRequest("South Wing", "Floor 2", "HQ");

        mockMvc.perform(post("/zones").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
