package com.truyengg.model.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 255, message = "Username không được vượt quá 255 ký tự")
    String username,

    @Size(max = 255, message = "Họ không được vượt quá 255 ký tự")
    String firstName,

    @Size(max = 255, message = "Tên không được vượt quá 255 ký tự")
    String lastName,

    String avatar
) {
}
