package com.securitysuite.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a signup attempt is made for a phone number that already has an account. */
@ResponseStatus(HttpStatus.CONFLICT)
public class PhoneAlreadyRegisteredException extends RuntimeException {
    public PhoneAlreadyRegisteredException(String phoneNumber) {
        super("Phone number already registered: " + phoneNumber);
    }
}
