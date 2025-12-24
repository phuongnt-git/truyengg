package com.truyengg.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;

@Entity
@Table(name = "token_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TokenBlacklist {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "token_hash", nullable = false, unique = true)
  String tokenHash;

  @Column(name = "expires_at", nullable = false)
  ZonedDateTime expiresAt;

  @Column(name = "revoked_at")
  ZonedDateTime revokedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  User user;

  @Column(length = 100)
  String reason;

}

