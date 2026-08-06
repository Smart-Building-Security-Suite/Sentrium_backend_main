package com.securitysuite.backend.auth;

import com.securitysuite.backend.auth.dto.*;
import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import com.securitysuite.backend.user.Role;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final OtpRepository otpRepository;
    private final PendingSignupRepository pendingSignupRepository;

    // ── Login ─────────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.phoneNumber(), request.password()));
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new NotFoundException("User not found"));
        UserDetails details = userDetailsService.loadUserByUsername(user.getPhoneNumber());
        setRefreshCookie(response, jwtService.generateRefreshToken(details));
        return new AuthResponse(jwtService.generateAccessToken(details), jwtService.getAccessExpirationSeconds(), UserSummary.from(user));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        try {
            // Check server-side revocation list before trusting the token.
            String jti = jwtService.extractJti(refreshToken);
            if (jti != null && revokedTokenRepository.existsByJti(jti)) {
                throw new BadCredentialsException("Refresh token has been revoked");
            }
            String phoneNumber = jwtService.extractEmail(refreshToken);
            User user = userRepository.findByPhoneNumber(phoneNumber)
                    .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
            UserDetails details = userDetailsService.loadUserByUsername(phoneNumber);
            if (!jwtService.isTokenValid(refreshToken, details)) {
                throw new BadCredentialsException("Invalid refresh token");
            }
            // Rotate: revoke the old token, issue a new one.
            revokeToken(refreshToken);
            setRefreshCookie(response, jwtService.generateRefreshToken(details));
            return new AuthResponse(jwtService.generateAccessToken(details), jwtService.getAccessExpirationSeconds(), UserSummary.from(user));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid refresh token");
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void clearRefreshCookie(HttpServletResponse response, String refreshToken) {
        // Revoke the token server-side so it cannot be reused even if the cookie is captured.
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                revokeToken(refreshToken);
            } catch (Exception ex) {
                log.warn("Failed to revoke refresh token on logout: {}", ex.getMessage());
            }
        }
        response.addHeader("Set-Cookie", ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build()
                .toString());
    }

    // ── OTP: Request ─────────────────────────────────────────────────────────

    @Transactional
    public OtpRequestResponse requestOtp(String phoneNumber) {
        // 1. Guard: phone already has an account
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new PhoneAlreadyRegisteredException(phoneNumber);
        }

        Instant now = Instant.now();

        // 2. Rate-limit: reject if a valid OTP was created within the last 30 seconds
        otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(rec -> rec.getCreatedAt().isAfter(now.minusSeconds(30)))
                .ifPresent(rec -> { throw new OtpRateLimitException(); });

        // 3. Generate 6-digit OTP
        String otp = String.format("%06d", (int) (Math.random() * 1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        // 4. Persist
        OtpRecord record = OtpRecord.builder()
                .phoneNumber(phoneNumber)
                .otpHash(otpHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(300))
                .verified(false)
                .attempts(0)
                .build();
        otpRepository.save(record);

        // 5. Stub: log OTP and return in response until SMS service is wired up
        log.info("OTP for {}: {}", phoneNumber, otp);

        return OtpRequestResponse.of(phoneNumber, now, otp);
    }

    // ── OTP: Verify ───────────────────────────────────────────────────────────

    @Transactional
    public OtpVerifyResponse verifyOtp(String phoneNumber, String otp) {
        Instant now = Instant.now();

        OtpRecord record = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new OtpInvalidException("OTP expired or not found"));

        // Check expiry
        if (record.getExpiresAt().isBefore(now)) {
            otpRepository.delete(record);
            throw new OtpInvalidException("OTP expired or not found");
        }

        // Increment attempt count first
        record.setAttempts(record.getAttempts() + 1);

        if (record.getAttempts() > 5) {
            otpRepository.delete(record);
            throw new OtpInvalidException("Too many failed attempts. Please request a new OTP.");
        }

        // Verify the code
        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            otpRepository.save(record); // persist incremented attempts
            int remaining = 5 - record.getAttempts();
            throw new OtpInvalidException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Success — mark verified and clean up
        record.setVerified(true);
        otpRepository.delete(record);

        // Issue a signup token valid for 10 minutes
        String signupToken = UUID.randomUUID().toString();
        PendingSignup pending = PendingSignup.builder()
                .phoneNumber(phoneNumber)
                .signupToken(signupToken)
                .createdAt(now)
                .expiresAt(now.plusSeconds(600))
                .used(false)
                .build();
        // Remove any stale pending signups for this number first
        pendingSignupRepository.deleteByPhoneNumber(phoneNumber);
        pendingSignupRepository.save(pending);

        return OtpVerifyResponse.of(phoneNumber, signupToken);
    }

    // ── Password Reset: Request ───────────────────────────────────────────────

    @Transactional
    public OtpRequestResponse requestPasswordResetOtp(String phoneNumber) {
        // 1. Guard: phone must have an existing account
        if (!userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new NotFoundException("No account found with this phone number");
        }

        Instant now = Instant.now();

        // 2. Rate-limit: reject if a valid OTP was created within the last 30 seconds
        otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(rec -> rec.getCreatedAt().isAfter(now.minusSeconds(30)))
                .ifPresent(rec -> { throw new OtpRateLimitException(); });

        // 3. Generate 6-digit OTP
        String otp = String.format("%06d", (int) (Math.random() * 1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        // 4. Persist
        OtpRecord record = OtpRecord.builder()
                .phoneNumber(phoneNumber)
                .otpHash(otpHash)
                .createdAt(now)
                .expiresAt(now.plusSeconds(300))
                .verified(false)
                .attempts(0)
                .build();
        otpRepository.save(record);

        // 5. Stub: log OTP and return in response until SMS service is wired up
        log.info("Password reset OTP for {}: {}", phoneNumber, otp);

        return OtpRequestResponse.of(phoneNumber, now, otp);
    }

    // ── Password Reset: Verify ────────────────────────────────────────────────

    @Transactional
    public OtpVerifyResponse verifyPasswordResetOtp(String phoneNumber, String otp) {
        // Verify user exists
        if (!userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new NotFoundException("No account found with this phone number");
        }

        Instant now = Instant.now();

        OtpRecord record = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new OtpInvalidException("OTP expired or not found"));

        // Check expiry
        if (record.getExpiresAt().isBefore(now)) {
            otpRepository.delete(record);
            throw new OtpInvalidException("OTP expired or not found");
        }

        // Increment attempt count first
        record.setAttempts(record.getAttempts() + 1);

        if (record.getAttempts() > 5) {
            otpRepository.delete(record);
            throw new OtpInvalidException("Too many failed attempts. Please request a new OTP.");
        }

        // Verify the code
        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            otpRepository.save(record); // persist incremented attempts
            int remaining = 5 - record.getAttempts();
            throw new OtpInvalidException("Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // Success — mark verified and clean up
        record.setVerified(true);
        otpRepository.delete(record);

        // Issue a password reset token valid for 10 minutes
        String resetToken = UUID.randomUUID().toString();
        PendingSignup pending = PendingSignup.builder()
                .phoneNumber(phoneNumber)
                .signupToken(resetToken) // reusing same field for reset token
                .createdAt(now)
                .expiresAt(now.plusSeconds(600))
                .used(false)
                .build();
        // Remove any stale pending resets for this number first
        pendingSignupRepository.deleteByPhoneNumber(phoneNumber);
        pendingSignupRepository.save(pending);

        return OtpVerifyResponse.of(phoneNumber, resetToken);
    }

    // ── Password Reset: Complete ──────────────────────────────────────────────

    @Transactional
    public void completePasswordReset(String resetToken, String newPassword) {
        Instant now = Instant.now();

        PendingSignup pending = pendingSignupRepository.findBySignupToken(resetToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        if (pending.isUsed() || pending.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("Invalid or expired reset token");
        }

        // Mark token as consumed
        pending.setUsed(true);
        pendingSignupRepository.save(pending);

        // Update user password
        User user = userRepository.findByPhoneNumber(pending.getPhoneNumber())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset completed for user: {}", pending.getPhoneNumber());
    }

    // ── Signup: Complete ──────────────────────────────────────────────────────

    @Transactional
    public AuthResponse completeSignup(String signupToken, String name, String password, Role role, HttpServletResponse response) {
        Instant now = Instant.now();

        PendingSignup pending = pendingSignupRepository.findBySignupToken(signupToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired signup token"));

        if (pending.isUsed() || pending.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("Invalid or expired signup token");
        }

        // Mark token as consumed
        pending.setUsed(true);
        pendingSignupRepository.save(pending);

        // Create the user account
        User user = new User();
        user.setPhoneNumber(pending.getPhoneNumber());
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);

        log.info("New user registered via OTP flow: {} ({})", pending.getPhoneNumber(), role);

        UserDetails details = userDetailsService.loadUserByUsername(user.getPhoneNumber());
        setRefreshCookie(response, jwtService.generateRefreshToken(details));
        return new AuthResponse(jwtService.generateAccessToken(details), jwtService.getAccessExpirationSeconds(), UserSummary.from(user));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void revokeToken(String refreshToken) {
        try {
            String jti = jwtService.extractJti(refreshToken);
            Instant expiry = jwtService.extractExpiry(refreshToken);
            if (jti != null && !revokedTokenRepository.existsByJti(jti)) {
                RevokedToken revoked = new RevokedToken();
                revoked.setJti(jti);
                revoked.setExpiresAt(expiry);
                revokedTokenRepository.save(revoked);
            }
        } catch (Exception ex) {
            log.warn("Could not record token revocation: {}", ex.getMessage());
        }
    }

    /** Nightly cleanup of expired revoked tokens and OTP records. */
    @Scheduled(cron = "0 0 3 * * *")
    public void pruneExpiredRecords() {
        Instant now = Instant.now();
        revokedTokenRepository.deleteExpiredBefore(now);
        otpRepository.deleteByExpiresAtBefore(now);
        log.info("Pruned expired revoked tokens and OTP records");
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader("Set-Cookie", ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtService.getRefreshExpirationSeconds())
                .build()
                .toString());
    }
}
