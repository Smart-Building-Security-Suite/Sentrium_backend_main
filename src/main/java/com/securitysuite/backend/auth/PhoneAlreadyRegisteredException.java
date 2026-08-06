package com.securitysuite.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a signup attempt is made for a phone number that already has an account. */
@ResponseStatus(HttpStatus.CONFLICT)
public class PhoneAlreadyRegisteredException extends RuntimeException {
    public PhoneAlreadyRegisteredException(String phoneNumber) {
        // Do NOT include phoneNumber in message - prevents user enumeration
        super("This phone number is already registered. Please login instead.");
    }
}
