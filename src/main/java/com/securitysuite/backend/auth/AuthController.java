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
    @Operation(summary = "Authenticate user with phone number and password",
               description = "Authenticates a user using their phone number and password. Returns access and refresh tokens upon successful authentication.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token",
               description = "Generates a new access token using a valid refresh token. The refresh token can be provided either as an HttpOnly cookie or in the request body. Implements token rotation for enhanced security.")
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
    @Operation(summary = "Log out the current user",
               description = "Revokes the refresh token server-side and clears the refresh token cookie. This prevents token reuse even if captured.")
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
    @Operation(summary = "Get the currently authenticated user",
               description = "Retrieves the profile information of the currently authenticated user including ID, name, phone number, and role.")
    public UserSummary me(@AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByPhoneNumber(principal.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return UserSummary.from(user);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user's profile",
               description = "Allows the authenticated user to update their display name. Other profile fields are immutable.")
    public UserSummary updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByPhoneNumber(principal.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
            userRepository.save(user);
        }
        return UserSummary.from(user);
    }

    // ── OTP Signup Flow ───────────────────────────────────────────────────────

    @PostMapping("/signup/otp/request")
    @Operation(summary = "Request an OTP for phone number verification",
               description = "Initiates the signup flow by sending a one-time password to the provided phone number. Rate-limited to prevent abuse (max 1 request per 30 seconds per phone number).")
    public ResponseEntity<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequestDto request) {
        OtpRequestResponse result = authService.requestOtp(request.phoneNumber());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/signup/otp/verify")
    @Operation(summary = "Verify the OTP and receive a signup token",
               description = "Validates the OTP sent to the user's phone. Upon successful verification, returns a signup token valid for 10 minutes. Maximum 5 verification attempts allowed.")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyDto request) {
        OtpVerifyResponse result = authService.verifyOtp(request.phoneNumber(), request.otp());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/signup/complete")
    @Operation(summary = "Complete signup using the signupToken from OTP verification",
               description = "Final step of the signup process. Creates a new user account with the provided name, password, and role. The signup token must be valid and not expired. Returns access and refresh tokens upon success.")
    public ResponseEntity<AuthResponse> completeSignup(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.completeSignup(request.signupToken(), request.name(), request.password(), request.role(), response);
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    // ── Password Reset Flow ───────────────────────────────────────────────────

    @PostMapping("/password/reset/request")
    @Operation(summary = "Request an OTP to reset password",
               description = "Initiates the password reset flow by sending a one-time password to the registered phone number. The phone number must be associated with an existing account. Rate-limited to prevent abuse.")
    public ResponseEntity<OtpRequestResponse> requestPasswordReset(@Valid @RequestBody OtpRequestDto request) {
        OtpRequestResponse result = authService.requestPasswordResetOtp(request.phoneNumber());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/password/reset/verify")
    @Operation(summary = "Verify the OTP and receive a password reset token",
               description = "Validates the password reset OTP. Upon successful verification, returns a reset token valid for 10 minutes. Maximum 5 verification attempts allowed.")
    public ResponseEntity<OtpVerifyResponse> verifyPasswordResetOtp(@Valid @RequestBody OtpVerifyDto request) {
        OtpVerifyResponse result = authService.verifyPasswordResetOtp(request.phoneNumber(), request.otp());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/password/reset/complete")
    @Operation(summary = "Complete password reset using the resetToken",
               description = "Final step of the password reset process. Updates the user's password using a valid reset token. The reset token must not be expired or already used.")
    public ResponseEntity<Void> completePasswordReset(@Valid @RequestBody PasswordResetCompleteRequest request) {
        authService.completePasswordReset(request.resetToken(), request.newPassword());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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
