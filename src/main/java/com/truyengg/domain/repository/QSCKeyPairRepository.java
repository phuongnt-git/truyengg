package com.truyengg.domain.repository;

import com.truyengg.domain.entity.QSCKeyPairEntity;
import com.truyengg.domain.enums.KeyType;
import com.truyengg.domain.enums.KeyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QSCKeyPairRepository extends JpaRepository<QSCKeyPairEntity, Long> {

  Optional<QSCKeyPairEntity> findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(
      KeyType keyType, KeyUsage keyUsage);

  List<QSCKeyPairEntity> findByKeyTypeAndIsActiveTrue(KeyType keyType);

  List<QSCKeyPairEntity> findByIsActiveFalseAndExpiresAtBefore(ZonedDateTime cutoffDate);

  @Modifying
  @Query("DELETE FROM QSCKeyPairEntity q WHERE q.isActive = false AND q.expiresAt < :cutoffDate")
  int deleteExpiredKeys(ZonedDateTime cutoffDate);
}

