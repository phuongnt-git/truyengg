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
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  User user;

  @Column(nullable = false, unique = true, length = 500)
  String token;

  @Column(name = "expires_at", nullable = false)
  ZonedDateTime expiresAt;

  @Column(name = "created_at")
  ZonedDateTime createdAt;

  @Column(name = "last_used_at")
  ZonedDateTime lastUsedAt;

  @Column(name = "ip_address", length = 45)
  String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  String userAgent;

  public boolean isExpired() {
    return expiresAt.isBefore(ZonedDateTime.now());
  }
}

