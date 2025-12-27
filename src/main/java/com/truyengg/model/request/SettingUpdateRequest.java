package com.truyengg.model.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating a setting value
 */
public record SettingUpdateRequest(
    @NotBlank(message = "Full key is required")
    String fullKey,

    String value
) {
}

