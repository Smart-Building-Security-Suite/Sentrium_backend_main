package com.securitysuite.backend.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class TestErrorController {
        @GetMapping("/test/bad-credentials")
        public void triggerBadCredentials() {
            throw new BadCredentialsException("Bad creds");
        }

        @GetMapping("/test/not-found")
        public void triggerNotFound() {
            throw new NotFoundException("Zone not found");
        }
    }

    @Test
    @DisplayName("Handle BadCredentialsException - Returns 401 with standard ApiError shape")
    void handleBadCredentials() throws Exception {
        mockMvc.perform(get("/test/bad-credentials")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.path").value("/test/bad-credentials"));
    }

    @Test
    @DisplayName("Handle NotFoundException - Returns 404 with standard ApiError shape")
    void handleNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Zone not found"))
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }
}

