package com.securitysuite.backend.auth;

import com.securitysuite.backend.auth.dto.AuthResponse;
import com.securitysuite.backend.auth.dto.LoginRequest;
import com.securitysuite.backend.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(extractRefreshToken(request), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Pass the refresh token so AuthService can revoke it server-side.
        String refreshToken = extractRefreshTokenOrNull(request);
        authService.clearRefreshCookie(response, refreshToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Missing refresh token");
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Missing refresh token"));
    }

    private String extractRefreshTokenOrNull(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
