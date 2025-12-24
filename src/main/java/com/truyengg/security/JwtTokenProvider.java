package com.truyengg.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

import static io.jsonwebtoken.security.Keys.hmacShaKeyFor;
import static java.lang.Long.parseLong;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Component
@Slf4j
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  public JwtTokenProvider(@Value("${truyengg.jwt.secret:your-secret-key-change-in-production-min-256-bits}") String secret,
                          @Value("${truyengg.jwt.access-token-expiration:3600000}") long accessTokenExpiration,
                          @Value("${truyengg.jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
    this.secretKey = hmacShaKeyFor(secret.getBytes(UTF_8));
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  /**
   * Generate access token (short-lived)
   */
  public String generateAccessToken(Long userId, String email, String role) {
    var now = new Date();
    var expiryDate = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("role", role)
        .claim("type", "access")
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(secretKey)
        .compact();
  }

  /**
   * Generate refresh token (long-lived)
   */
  public String generateRefreshToken(Long userId, String email, String role) {
    var now = new Date();
    var expiryDate = new Date(now.getTime() + refreshTokenExpiration);
    var tokenId = randomUUID().toString();

    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("role", role)
        .claim("type", "refresh")
        .claim("tokenId", tokenId)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(secretKey)
        .compact();
  }

  public Long getUserIdFromToken(String token) {
    try {
      var claims = getClaimsFromToken(token);
      return parseLong(claims.getSubject());
    } catch (Exception e) {
      return 0L;
    }
  }

  public String getRoleFromToken(String token) {
    try {
      var claims = getClaimsFromToken(token);
      return claims.get("role", String.class);
    } catch (Exception e) {
      return EMPTY;
    }
  }

  public String getTokenType(String token) {
    try {
      var claims = getClaimsFromToken(token);
      return claims.get("type", String.class);
    } catch (Exception e) {
      return EMPTY;
    }
  }

  public Date getExpirationDateFromToken(String token) {
    try {
      var claims = getClaimsFromToken(token);
      return claims.getExpiration();
    } catch (Exception e) {
      return null;
    }
  }

  public boolean validateToken(String token) {
    try {
      getClaimsFromToken(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      var type = getTokenType(token);
      return "refresh".equals(type);
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isAccessToken(String token) {
    try {
      var type = getTokenType(token);
      return "access".equals(type);
    } catch (Exception e) {
      return false;
    }
  }

  private Claims getClaimsFromToken(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}

