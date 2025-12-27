package com.truyengg.security.passkey.model;

import jakarta.validation.constraints.Email;

public record PasskeyStartAuthenticationRequest(
    @Email(message = "Invalid email format")
    String email
) {
}

