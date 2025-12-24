package com.truyengg.service;

import com.truyengg.domain.entity.RefreshToken;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.repository.RefreshTokenRepository;
import com.truyengg.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.time.ZoneId.systemDefault;
import static java.time.ZonedDateTime.now;
import static java.util.Comparator.comparing;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;

  @Value("${truyengg.jwt.max-refresh-tokens-per-user:5}")
  private int maxRefreshTokensPerUser;

  @Transactional
  public RefreshToken createRefreshToken(User user, String ipAddress, String userAgent) {
    if (hasTooManyTokens(user.getId(), maxRefreshTokensPerUser)) {
      var tokens = refreshTokenRepository.findAll().stream()
          .filter(rt -> rt.getUser().getId().equals(user.getId()))
          .sorted(comparing(RefreshToken::getCreatedAt))
          .toList();

      var tokensToDelete = tokens.size() - maxRefreshTokensPerUser + 1;
      for (var i = 0; i < tokensToDelete; i++) {
        refreshTokenRepository.delete(tokens.get(i));
      }
    }

    var token = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRoles().name());
    var expirationDate = jwtTokenProvider.getExpirationDateFromToken(token);
    var expiresAt = expirationDate.toInstant()
        .atZone(systemDefault());

    var refreshToken = RefreshToken.builder()
        .user(user)
        .token(token)
        .expiresAt(expiresAt)
        .createdAt(now())
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        .build();

    return refreshTokenRepository.save(refreshToken);
  }

  @Transactional
  public User verifyRefreshToken(String token) {
    if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
      throw new AuthenticationServiceException("Invalid refresh token");
    }

    var refreshToken = refreshTokenRepository.findByToken(token)
        .orElseThrow(() -> new AuthenticationServiceException("Refresh token not found"));

    if (refreshToken.isExpired()) {
      refreshTokenRepository.delete(refreshToken);
      throw new AuthenticationServiceException("Refresh token has expired");
    }

    refreshToken.setLastUsedAt(now());
    refreshTokenRepository.save(refreshToken);

    return refreshToken.getUser();
  }

  @Transactional
  public void deleteRefreshToken(String token) {
    refreshTokenRepository.findByToken(token)
        .ifPresent(refreshTokenRepository::delete);
  }

  @Transactional
  public void cleanupExpiredTokens() {
    try {
      refreshTokenRepository.deleteExpiredTokens(now());
    } catch (Exception e) {
      log.error("Error cleaning up expired refresh tokens", e);
    }
  }

  public boolean hasTooManyTokens(Long userId, int maxTokens) {
    return refreshTokenRepository.countByUserId(userId) >= maxTokens;
  }
}
