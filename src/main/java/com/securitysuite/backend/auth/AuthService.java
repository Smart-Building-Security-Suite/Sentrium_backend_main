package com.securitysuite.backend.auth;

import com.securitysuite.backend.auth.dto.AuthResponse;
import com.securitysuite.backend.auth.dto.LoginRequest;
import com.securitysuite.backend.auth.dto.RegisterRequest;
import com.securitysuite.backend.auth.dto.UserSummary;
import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.security.CustomUserDetailsService;
import com.securitysuite.backend.security.JwtService;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.user.Role;
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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("email: already registered");
        }
        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // All public registrations default to SECURITY_OFFICER.
        // ADMIN accounts must be provisioned directly in the database.
        user.setRole(Role.SECURITY_OFFICER);
        user.setActive(true);
        userRepository.save(user);
        log.info("New user registered: {} ({})", request.email(), user.getRole());
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        return new AuthResponse(jwtService.generateAccessToken(details), jwtService.getAccessExpirationSeconds(), UserSummary.from(user));
    }

    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("User not found"));
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        setRefreshCookie(response, jwtService.generateRefreshToken(details));
        return new AuthResponse(jwtService.generateAccessToken(details), jwtService.getAccessExpirationSeconds(), UserSummary.from(user));
    }

    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        try {
            // Check server-side revocation list before trusting the token.
            String jti = jwtService.extractJti(refreshToken);
            if (jti != null && revokedTokenRepository.existsByJti(jti)) {
                throw new BadCredentialsException("Refresh token has been revoked");
            }
            String email = jwtService.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
            UserDetails details = userDetailsService.loadUserByUsername(email);
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

    /** Nightly cleanup of expired revoked tokens. */
    @Scheduled(cron = "0 0 3 * * *")
    public void pruneExpiredRevokedTokens() {
        revokedTokenRepository.deleteExpiredBefore(Instant.now());
        log.info("Pruned expired revoked tokens");
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
