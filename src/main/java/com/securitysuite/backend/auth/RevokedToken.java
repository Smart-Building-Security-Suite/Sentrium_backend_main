package com.securitysuite.backend.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records revoked refresh tokens so server-side logout is effective
 * even before the token's natural TTL expires.
 */
@Entity
@Table(name = "revoked_token",
        indexes = @Index(name = "idx_revoked_token_jti", columnList = "jti", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class RevokedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** JWT ID (jti) claim — unique identifier of the revoked token. */
    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    /** When this token naturally expires — used for housekeeping. */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant revokedAt = Instant.now();
}
