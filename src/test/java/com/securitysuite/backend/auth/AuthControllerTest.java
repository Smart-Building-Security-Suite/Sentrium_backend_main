package com.securitysuite.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securitysuite.backend.auth.dto.*;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import com.securitysuite.backend.user.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    @DisplayName("POST /auth/signup/otp/request - Success")
    void requestOtpSuccess() throws Exception {
        String phoneNumber = "+1234567890";
        OtpRequestResponse response = OtpRequestResponse.of(phoneNumber, Instant.now(), "123456");

        given(authService.requestOtp(eq(phoneNumber))).willReturn(response);

        mockMvc.perform(post("/auth/signup/otp/request")
                        .param("phoneNumber", phoneNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(phoneNumber));
    }

    @Test
    @DisplayName("POST /auth/signup/complete - Success")
    void completeSignupSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest("signup-token-123", "Test User", "Secret123!", Role.SECURITY_OFFICER);
        UserSummary summary = new UserSummary(UUID.randomUUID(), "Test User", "+1234567890", Role.SECURITY_OFFICER);
        AuthResponse response = new AuthResponse("mock-access-token", 900L, summary);

        given(authService.completeSignup(eq("signup-token-123"), eq("Test User"), eq("Secret123!"), eq(Role.SECURITY_OFFICER), any())).willReturn(response);

        mockMvc.perform(post("/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.fullName").value("Test User"))
                .andExpect(jsonPath("$.user.role").value("SECURITY_OFFICER"));
    }

    @Test
    @DisplayName("POST /auth/login - Success")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("+1234567890", "Secret123!");
        UserSummary summary = new UserSummary(UUID.randomUUID(), "Test User", "+1234567890", Role.SECURITY_OFFICER);
        AuthResponse response = new AuthResponse("mock-access-token", 900L, summary);

        given(authService.login(any(LoginRequest.class), any())).willReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.user.fullName").value("Test User"));
    }

    @Test
    @DisplayName("POST /auth/refresh - With Valid Refresh Token Cookie")
    void refreshSuccess() throws Exception {
        UserSummary summary = new UserSummary(UUID.randomUUID(), "Test User", "+1234567890", Role.SECURITY_OFFICER);
        AuthResponse response = new AuthResponse("new-access-token", 900L, summary);

        given(authService.refresh(eq("valid-refresh-cookie"), any())).willReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "valid-refresh-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /auth/logout - Success 204")
    void logoutSuccess() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());

        // clearRefreshCookie now takes (HttpServletResponse, String)
        verify(authService).clearRefreshCookie(any(), any());
    }
}
