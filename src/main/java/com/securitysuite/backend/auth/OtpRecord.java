package com.securitysuite.backend.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stores a BCrypt-hashed one-time-password issued during the phone-number
 * verification step of the signup flow.
 */
@Entity
@Table(name = "otp_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phoneNumber;

    /** BCrypt hash of the 6-digit OTP — never store the plaintext OTP. */
    @Column(nullable = false)
    private String otpHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** createdAt + 300 seconds */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean verified = false;

    /** Number of failed verification attempts for this OTP. */
    @Column(nullable = false)
    private int attempts = 0;
}
