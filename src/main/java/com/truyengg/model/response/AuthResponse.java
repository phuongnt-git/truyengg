package com.truyengg.model.response;

public record AuthResponse(
    String token,
    UserResponse user
) {
}
