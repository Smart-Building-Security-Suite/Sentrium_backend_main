package com.securitysuite.backend.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A short-lived token issued after successful OTP verification.
 * The client presents this token at POST /auth/signup/complete to create their account.
 */
@Entity
@Table(name = "pending_signups")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phoneNumber;

    /** UUID-based token presented by the client at the complete-signup step. */
    @Column(nullable = false, unique = true)
    private String signupToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** createdAt + 600 seconds (10 minutes) */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;
}
