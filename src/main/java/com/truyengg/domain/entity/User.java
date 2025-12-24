package com.truyengg.domain.entity;

import com.truyengg.domain.enums.Gender;
import com.truyengg.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 100)
  String email;

  @Column(nullable = false)
  String password;

  @Column(length = 255)
  String username;

  @Column(length = 255)
  String avatar;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false, columnDefinition = "user_role_enum")
  UserRole roles = UserRole.USER;

  @Column(name = "banned_until")
  ZonedDateTime bannedUntil;

  @Builder.Default
  Long xu = 0L;

  @Builder.Default
  Long points = 0L;

  @Builder.Default
  Integer level = 1;

  @Builder.Default
  Integer progress = 0;

  @Column(name = "last_name", length = 255)
  String lastName;

  @Column(name = "first_name", length = 255)
  String firstName;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(columnDefinition = "user_gender_enum")
  Gender gender;

  @Column(name = "type_rank")
  @Builder.Default
  Integer typeRank = 0;

  @Column(name = "reset_token", length = 100)
  String resetToken;

  @Column(name = "failed_attempts")
  @Builder.Default
  Integer failedAttempts = 0;

  @Column(name = "lockout_time")
  ZonedDateTime lockoutTime;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  ZonedDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  ZonedDateTime updatedAt;
}

