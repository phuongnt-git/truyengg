package com.truyengg.model.response;

import com.truyengg.domain.entity.User;
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

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getUsername(),
        user.getAvatar(),
        user.getRoles(),
        user.getXu(),
        user.getPoints(),
        user.getLevel(),
        user.getProgress(),
        user.getLastName(),
        user.getFirstName(),
        user.getGender(),
        user.getTypeRank(),
        user.getCreatedAt()
    );
  }
}
