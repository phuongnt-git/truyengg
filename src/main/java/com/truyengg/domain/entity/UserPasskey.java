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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_passkeys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPasskey {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  User user;

  @Column(name = "credential_id", nullable = false, unique = true)
  byte[] credentialId;

  @Column(name = "public_key", nullable = false)
  byte[] publicKey;

  @Column(name = "sign_count", nullable = false)
  @Builder.Default
  Long signCount = 0L;

  @Column(name = "aaguid")
  byte[] aaguid;

  @Column(name = "device_name", nullable = false)
  String deviceName;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "transports", columnDefinition = "TEXT[]")
  List<String> transports;

  @Column(name = "is_discoverable", nullable = false)
  @Builder.Default
  Boolean isDiscoverable = false;

  @Column(name = "last_used_at")
  ZonedDateTime lastUsedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  ZonedDateTime createdAt = ZonedDateTime.now();
}

