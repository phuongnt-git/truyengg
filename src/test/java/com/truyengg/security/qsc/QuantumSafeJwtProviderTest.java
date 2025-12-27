package com.truyengg.security.qsc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QuantumSafeJwtProviderTest {

  @Autowired
  private QuantumSafeJwtProvider jwtProvider;

  @Test
  void shouldGenerateAccessToken() {
    // When
    var token = jwtProvider.generateAccessToken(1L, "admin@test.com", "ADMIN");

    // Then
    assertNotNull(token);
    assertTrue(token.split("\\.").length == 3);
  }

  @Test
  void shouldGenerateRefreshToken() {
    // When
    var token = jwtProvider.generateRefreshToken(1L, "admin@test.com", "ADMIN");

    // Then
    assertNotNull(token);
    assertTrue(token.split("\\.").length == 3);
    assertNotNull(jwtProvider.getJti(token)); // Should have JTI
  }

  @Test
  void shouldValidateToken() {
    // Given
    var token = jwtProvider.generateAccessToken(1L, "admin@test.com", "ADMIN");

    // When
    var isValid = jwtProvider.validateToken(token);

    // Then
    assertTrue(isValid);
  }

  @Test
  void shouldExtractUserId() {
    // Given
    var token = jwtProvider.generateAccessToken(123L, "test@test.com", "USER");

    // When
    var userId = jwtProvider.getUserIdFromToken(token);

    // Then
    assertEquals(123L, userId);
  }

  @Test
  void shouldExtractJtiFromRefreshToken() {
    // Given
    var token = jwtProvider.generateRefreshToken(1L, "test@test.com", "USER");

    // When
    var jti = jwtProvider.getJti(token);

    // Then
    assertNotNull(jti);
    assertFalse(jti.isEmpty());
  }
}

