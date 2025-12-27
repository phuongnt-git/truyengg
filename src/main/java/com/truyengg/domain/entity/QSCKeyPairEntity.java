package com.truyengg.domain.entity;

import com.truyengg.domain.enums.KeyType;
import com.truyengg.domain.enums.KeyUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.ZonedDateTime;

@Entity
@Table(name = "qsc_key_pairs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QSCKeyPairEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "key_type", nullable = false, length = 20)
  KeyType keyType;

  @Column(nullable = false, length = 50)
  String algorithm;

  @Enumerated(EnumType.STRING)
  @Column(name = "key_usage", nullable = false, length = 50)
  KeyUsage keyUsage;

  @Column(name = "public_key", nullable = false, columnDefinition = "BYTEA")
  byte[] publicKey;

  @Column(name = "private_key", nullable = false, columnDefinition = "BYTEA")
  byte[] privateKey;

  @Column(nullable = false, length = 128)
  String fingerprint;

  @Column(name = "created_at", nullable = false)
  ZonedDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  ZonedDateTime expiresAt;

  @Column(name = "is_active")
  @Builder.Default
  boolean isActive = true;

  @Column(name = "rotation_count")
  @Builder.Default
  int rotationCount = 0;
}

