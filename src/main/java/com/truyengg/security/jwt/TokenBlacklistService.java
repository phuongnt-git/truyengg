package com.truyengg.security.jwt;

import com.truyengg.domain.entity.TokenBlacklist;
import com.truyengg.domain.repository.TokenBlacklistRepository;
import com.truyengg.domain.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;

import static java.lang.Integer.toHexString;
import static java.security.MessageDigest.getInstance;
import static java.time.ZoneId.systemDefault;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenBlacklistService {

  TokenBlacklistRepository tokenBlacklistRepository;
  UserRepository userRepository;
  JwtTokenProvider jwtTokenProvider;

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
          .reason(defaultIfBlank(reason, "logout"))
          .build();

      if (userId != null) {
        userRepository.findById(userId).ifPresent(blacklistEntry::setUser);
      }

      tokenBlacklistRepository.save(blacklistEntry);
    } catch (Exception e) {
      log.warn("Error blacklisting token: {}", getRootCauseMessage(e));
    }
  }

  @Transactional(readOnly = true)
  public boolean isTokenBlacklisted(String token) {
    try {
      var tokenHash = hashToken(token);
      return tokenBlacklistRepository.existsByTokenHash(tokenHash);
    } catch (Exception e) {
      log.warn("Error checking token blacklist: {}", getRootCauseMessage(e));
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
      log.warn("Error cleaning up expired tokens", e);
    }
  }

  /**
   * Generate hash for token (for storage)
   */
  private String hashToken(String token) {
    try {
      var digest = getInstance("SHA-256");
      var hash = digest.digest(token.getBytes());
      var hexString = new StringBuilder();
      for (var b : hash) {
        var hex = toHexString(0xff & b);
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
