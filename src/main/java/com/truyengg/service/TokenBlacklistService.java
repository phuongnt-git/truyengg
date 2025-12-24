package com.truyengg.service;

import com.truyengg.domain.entity.TokenBlacklist;
import com.truyengg.domain.repository.TokenBlacklistRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.time.ZoneId.systemDefault;
import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

  private final TokenBlacklistRepository tokenBlacklistRepository;
  private final UserRepository userRepository;
  private final JwtTokenProvider jwtTokenProvider;

  @Transactional
  public void blacklistToken(String token, Long userId, String reason) {
    try {
      var tokenHash = hashToken(token);
      var expirationDate = jwtTokenProvider.getExpirationDateFromToken(token);
      var expiresAt = expirationDate.toInstant()
          .atZone(systemDefault());

      var blacklistEntry = TokenBlacklist.builder()
          .tokenHash(tokenHash)
          .expiresAt(expiresAt)
          .revokedAt(now())
          .reason(reason != null ? reason : "logout")
          .build();

      if (userId != null) {
        userRepository.findById(userId).ifPresent(blacklistEntry::setUser);
      }

      tokenBlacklistRepository.save(blacklistEntry);
    } catch (Exception e) {
      log.error("Error blacklisting token", e);
    }
  }

  @Transactional(readOnly = true)
  public boolean isTokenBlacklisted(String token) {
    try {
      var tokenHash = hashToken(token);
      return tokenBlacklistRepository.existsByTokenHash(tokenHash);
    } catch (Exception e) {
      log.error("Error checking token blacklist", e);
      return false;
    }
  }

  /**
   * Cleanup expired blacklisted tokens.
   * This method is now called by JobRunr instead of @Scheduled
   */
  @Transactional
  public void cleanupExpiredTokens() {
    try {
      tokenBlacklistRepository.deleteExpiredTokens(now());
    } catch (Exception e) {
      log.error("Error cleaning up expired tokens", e);
    }
  }

  /**
   * Generate hash for token (for storage)
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes());
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }
}
