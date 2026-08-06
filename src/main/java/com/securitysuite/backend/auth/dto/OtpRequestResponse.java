package com.securitysuite.backend.auth.dto;

import java.time.Instant;

public record OtpRequestResponse(String phoneNumber, Instant otpSentAt, int expiresIn, String otp) {
    /** Convenience factory with the standard 300-second expiry. */
    public static OtpRequestResponse of(String phoneNumber, Instant sentAt, String otp) {
        return new OtpRequestResponse(phoneNumber, sentAt, 300, otp);
    }
}
