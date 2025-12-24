package com.truyengg.model.response;

import com.truyengg.domain.enums.Gender;
import com.truyengg.domain.enums.UserRole;

import java.time.ZonedDateTime;

public record UserResponse(
    Long id,
    String email,
    String username,
    String avatar,
    UserRole roles,
    Long xu,
    Long points,
    Integer level,
    Integer progress,
    String lastName,
    String firstName,
    Gender gender,
    Integer typeRank,
    ZonedDateTime createdAt
) {
}

