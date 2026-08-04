package com.securitysuite.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when OTP verification fails (wrong code, expired, or too many attempts). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OtpInvalidException extends RuntimeException {
    public OtpInvalidException(String message) {
        super(message);
    }
}
