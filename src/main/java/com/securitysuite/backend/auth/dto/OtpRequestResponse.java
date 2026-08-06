package com.securitysuite.backend.auth.dto;

import java.time.Instant;

/**
 * SECURITY WARNING: The 'otp' field is included ONLY for development/testing purposes
 * until SMS service integration is complete. In production, this field should be null
 * and the OTP should only be sent via SMS.
 */
public record OtpRequestResponse(String phoneNumber, Instant otpSentAt, int expiresIn, String otp) {
    /** Convenience factory with the standard 300-second expiry. */
    public static OtpRequestResponse of(String phoneNumber, Instant sentAt, String otp) {
        return new OtpRequestResponse(phoneNumber, sentAt, 300, otp);
    }
}
