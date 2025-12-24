package com.truyengg.model.request;

import com.truyengg.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull(message = "Vai trò không được để trống")
    UserRole role
) {
}
