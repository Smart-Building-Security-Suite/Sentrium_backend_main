package com.securitysuite.backend.auth.dto;

import java.time.Instant;

public record OtpRequestResponse(String phoneNumber, Instant otpSentAt, int expiresIn) {
    /** Convenience factory with the standard 300-second expiry. */
    public static OtpRequestResponse of(String phoneNumber, Instant sentAt) {
        return new OtpRequestResponse(phoneNumber, sentAt, 300);
    }
}
