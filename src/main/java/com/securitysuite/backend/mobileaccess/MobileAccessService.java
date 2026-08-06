package com.securitysuite.backend.mobileaccess;

import com.securitysuite.backend.common.NotFoundException;
import com.securitysuite.backend.device.Device;
import com.securitysuite.backend.device.DeviceRepository;
import com.securitysuite.backend.user.User;
import com.securitysuite.backend.user.UserRepository;
import com.securitysuite.backend.zone.Zone;
import com.securitysuite.backend.zone.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileAccessService {
    private final MobileAccessTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;

    @Transactional
    public MobileAccessTokenDto generateToken(UUID userId, UUID deviceId, UUID zoneId,
                                              Integer durationMinutes, Integer maxUses, String purpose) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Device device = null;
        if (deviceId != null) {
            device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new NotFoundException("Device not found"));
        }

        Zone zone = null;
        if (zoneId != null) {
            zone = zoneRepository.findById(zoneId)
                    .orElseThrow(() -> new NotFoundException("Zone not found"));
        }

        if (device == null && zone == null) {
            throw new IllegalArgumentException("Must specify either deviceId or zoneId");
        }

        MobileAccessToken token = new MobileAccessToken();
        token.setUser(user);
        token.setDevice(device);
        token.setZone(zone);
        token.setQrCodeData("QR-" + UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(durationMinutes != null ? durationMinutes : 60, ChronoUnit.MINUTES));
        token.setUsesRemaining(maxUses);
        token.setPurpose(purpose);

        token = tokenRepository.save(token);
        log.info("Mobile access token generated: user={}, device={}, zone={}, expires={}",
                user.getName(), device != null ? device.getName() : "N/A",
                zone != null ? zone.getName() : "N/A", token.getExpiresAt());

        return MobileAccessTokenDto.from(token);
    }

    @Transactional
    public ValidationResult validateToken(String qrCodeData, UUID deviceId) {
        MobileAccessToken token = tokenRepository.findByQrCodeData(qrCodeData)
                .orElse(null);

        if (token == null) {
            return new ValidationResult(false, "Invalid QR code", null);
        }

        if (token.getRevoked()) {
            return new ValidationResult(false, "Token has been revoked", null);
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            return new ValidationResult(false, "Token has expired", null);
        }

        if (token.getUsesRemaining() != null && token.getUsedCount() >= token.getUsesRemaining()) {
            return new ValidationResult(false, "Token usage limit exceeded", null);
        }

        // Check if token is for specific device
        if (token.getDevice() != null && !token.getDevice().getId().equals(deviceId)) {
            return new ValidationResult(false, "Token not valid for this device", null);
        }

        // Check if device is in the allowed zone
        if (token.getZone() != null && token.getDevice() == null) {
            Device device = deviceRepository.findById(deviceId)
                    .orElse(null);
            if (device == null || !device.getZone().getId().equals(token.getZone().getId())) {
                return new ValidationResult(false, "Token not valid for this zone", null);
            }
        }

        // Success - increment usage count
        token.setUsedCount(token.getUsedCount() + 1);
        token.setLastUsedAt(Instant.now());
        tokenRepository.save(token);

        log.info("Mobile access token validated: user={}, device={}, usedCount={}",
                token.getUser().getName(), deviceId, token.getUsedCount());

        return new ValidationResult(true, "Access granted", MobileAccessTokenDto.from(token));
    }

    @Transactional
    public void revokeToken(UUID tokenId) {
        MobileAccessToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found"));
        token.setRevoked(true);
        tokenRepository.save(token);
        log.info("Mobile access token revoked: id={}", tokenId);
    }

    public List<MobileAccessTokenDto> getUserTokens(UUID userId) {
        return tokenRepository.findByUserId(userId).stream()
                .map(MobileAccessTokenDto::from)
                .toList();
    }

    public List<MobileAccessTokenDto> getUserActiveTokens(UUID userId) {
        return tokenRepository.findActiveTokensByUser(userId, Instant.now()).stream()
                .map(MobileAccessTokenDto::from)
                .toList();
    }

    public MobileAccessTokenDto getToken(UUID tokenId) {
        return MobileAccessTokenDto.from(tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found")));
    }

    public record ValidationResult(boolean granted, String message, MobileAccessTokenDto token) {}
}
