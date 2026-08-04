package com.securitysuite.backend.auth;

import com.securitysuite.backend.auth.dto.*;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public AuthResponse refresh(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) Map<String, String> body) {
        // Support both HttpOnly cookie and explicit request body { "refreshToken": "..." }
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Missing refresh token");
        }
        return authService.refresh(refreshToken, response);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) Map<String, String> body) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null && body != null) {
            refreshToken = body.get("refreshToken");
        }
        authService.clearRefreshCookie(response, refreshToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ── Current User ─────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    public UserSummary me(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByPhoneNumber(principal.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return UserSummary.from(user);
    }

    // ── OTP Signup Flow ───────────────────────────────────────────────────────

    @PostMapping("/signup/otp/request")
    @Operation(summary = "Request an OTP for phone number verification")
    public ResponseEntity<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        OtpRequestResponse result = authService.requestOtp(request.phoneNumber());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/signup/otp/verify")
    @Operation(summary = "Verify the OTP and receive a signup token")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        OtpVerifyResponse result = authService.verifyOtp(request.phoneNumber(), request.otp());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/signup/complete")
    @Operation(summary = "Complete signup using the signupToken from OTP verification")
    public ResponseEntity<AuthResponse> completeSignup(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.completeSignup(request.signupToken(), request.name(), request.password(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
