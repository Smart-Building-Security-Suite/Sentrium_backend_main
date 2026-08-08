package com.securitysuite.backend.alert;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.context.annotation.Import;
import com.securitysuite.backend.config.SecurityConfig;
import com.securitysuite.backend.security.JwtAuthFilter;
import com.securitysuite.backend.security.AuthRateLimitFilter;
import com.securitysuite.backend.security.RestAuthenticationEntryPoint;
import com.securitysuite.backend.security.RestAccessDeniedHandler;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthFilter.class, AuthRateLimitFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertService alertService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    private AlertDto sampleDto() {
        UUID zoneId = UUID.randomUUID();
        return new AlertDto(
                UUID.randomUUID(), zoneId, "Zone A", null, null,
                AlertSeverity.HIGH, AlertStatus.OPEN, "Motion detected",
                Instant.now(), null, null, null);
    }

    // ── Authorization tests (filters enabled — no addFilters=false) ──────────

    @Test
    @DisplayName("GET /alerts - anonymous returns 401")
    void listAlerts_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /alerts - authenticated user sees paginated results")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void listAlerts_authenticated_returnsPaginatedResults() throws Exception {
        // AlertService.list() returns List<Alert> — the controller maps to AlertDto
        Alert rawAlert = new Alert();
        rawAlert.setZone(new com.securitysuite.backend.zone.Zone());
        rawAlert.getZone().setId(java.util.UUID.randomUUID());
        rawAlert.getZone().setName("Zone A");
        rawAlert.setSeverity(AlertSeverity.HIGH);
        rawAlert.setStatus(AlertStatus.OPEN);
        rawAlert.setMessage("Motion detected");
        rawAlert.setCreatedAt(java.time.Instant.now());

        Page<Alert> page = new PageImpl<>(List.of(rawAlert));
        given(alertService.list(any(), any(), any(), any(), any())).willReturn(page);

        mockMvc.perform(get("/alerts").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /alerts - SECURITY_OFFICER can create alert")
    @WithMockUser(roles = "SECURITY_OFFICER")
    void createAlert_asOfficer_returns201() throws Exception {
        UUID zoneId = UUID.randomUUID();
        AlertController.AlertRequest request = new AlertController.AlertRequest(
                zoneId, null, AlertSeverity.HIGH, "Motion detected");
        Alert mockAlert = new Alert();
        mockAlert.setZone(new com.securitysuite.backend.zone.Zone());
        mockAlert.getZone().setId(zoneId);
        mockAlert.getZone().setName("Zone A");
        mockAlert.setSeverity(AlertSeverity.HIGH);
        mockAlert.setStatus(AlertStatus.OPEN);
        mockAlert.setMessage("Motion detected");
        mockAlert.setCreatedAt(Instant.now());

        given(alertService.create(any())).willReturn(mockAlert);

        mockMvc.perform(post("/alerts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("HIGH"));
    }

    @Test
    @DisplayName("POST /alerts - anonymous returns 401")
    void createAlert_anonymous_returns401() throws Exception {
        AlertController.AlertRequest request = new AlertController.AlertRequest(
                UUID.randomUUID(), null, AlertSeverity.HIGH, "Test");

        mockMvc.perform(post("/alerts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /alerts/{id}/resolve - ADMIN can resolve")
    @WithMockUser(roles = "ADMIN")
    void resolveAlert_asAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Alert mockAlert = new Alert();
        mockAlert.setZone(new com.securitysuite.backend.zone.Zone());
        mockAlert.getZone().setId(UUID.randomUUID());
        mockAlert.getZone().setName("Zone A");
        mockAlert.setSeverity(AlertSeverity.HIGH);
        mockAlert.setStatus(AlertStatus.RESOLVED);
        mockAlert.setMessage("Resolved");
        mockAlert.setCreatedAt(Instant.now());
        mockAlert.setResolvedAt(Instant.now());

        given(alertService.resolve(eq(id))).willReturn(mockAlert);

        mockMvc.perform(patch("/alerts/{id}/resolve", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    @DisplayName("PATCH /alerts/{id}/resolve - anonymous returns 401")
    void resolveAlert_anonymous_returns401() throws Exception {
        mockMvc.perform(patch("/alerts/{id}/resolve", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
