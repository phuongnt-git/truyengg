package com.truyengg.domain.repository;

import com.truyengg.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
  void deleteAllByUserId(Long userId);

  @Modifying
  @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
  void deleteExpiredTokens(ZonedDateTime now);

  @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user.id = :userId")
  long countByUserId(Long userId);
}

