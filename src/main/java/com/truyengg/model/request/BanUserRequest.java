package com.truyengg.model.request;

import jakarta.validation.constraints.NotBlank;

public record BanUserRequest(
    @NotBlank(message = "Thời gian cấm không được để trống")
    String duration
) {
}
