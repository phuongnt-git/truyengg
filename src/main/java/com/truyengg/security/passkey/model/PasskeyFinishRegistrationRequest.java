package com.truyengg.security.passkey.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyFinishRegistrationRequest(
    @NotBlank(message = "Device name is required")
    @Size(max = 255, message = "Device name must not exceed 255 characters")
    String deviceName,
    @NotBlank(message = "Credential response is required")
    String credential
) {
}

