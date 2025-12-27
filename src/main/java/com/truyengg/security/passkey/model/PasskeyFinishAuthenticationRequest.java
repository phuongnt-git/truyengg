package com.truyengg.security.passkey.model;

import jakarta.validation.constraints.NotBlank;

public record PasskeyFinishAuthenticationRequest(
    @NotBlank(message = "Credential response is required")
    String credential
) {
}

