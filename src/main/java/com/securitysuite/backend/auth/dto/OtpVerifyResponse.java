package com.securitysuite.backend.auth.dto;

public record OtpVerifyResponse(String phoneNumber, boolean phoneVerified, String signupToken) {
    /** Convenience factory — phoneVerified is always true on success. */
    public static OtpVerifyResponse of(String phoneNumber, String signupToken) {
        return new OtpVerifyResponse(phoneNumber, true, signupToken);
    }
}
