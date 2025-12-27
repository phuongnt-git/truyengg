package com.truyengg.domain.repository;

import com.truyengg.domain.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

  boolean existsByTokenHash(String tokenHash);

  @Modifying
  @Query("DELETE FROM TokenBlacklist tb WHERE tb.expiresAt < :now")
  void deleteExpiredTokens(ZonedDateTime now);
}

