package com.securitysuite.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an OTP is requested too soon after a previous OTP was sent. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpRateLimitException extends RuntimeException {
    public OtpRateLimitException() {
        super("OTP already sent recently. Please wait before requesting another.");
    }
}
