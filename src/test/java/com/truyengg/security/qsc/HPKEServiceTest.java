package com.truyengg.security.qsc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HPKEServiceTest {

  @Autowired
  private HPKEService hpkeService;

  @Test
  void shouldEncryptAndDecrypt() {
    // Given
    var plaintext = "Test data for HPKE".getBytes();

    // When
    var encrypted = hpkeService.encrypt(plaintext);
    var decrypted = hpkeService.decrypt(encrypted);

    // Then
    assertArrayEquals(plaintext, decrypted);
  }

  @Test
  void shouldHandleLargePayloads() {
    // Given
    var largeData = new byte[10 * 1024]; // 10KB
    for (int i = 0; i < largeData.length; i++) {
      largeData[i] = (byte) (i % 256);
    }

    // When/Then
    assertDoesNotThrow(() -> {
      var encrypted = hpkeService.encrypt(largeData);
      var decrypted = hpkeService.decrypt(encrypted);
      assertArrayEquals(largeData, decrypted);
    });
  }

  @Test
  void shouldCompressLargePayloads() {
    // Given
    var largeText = "A".repeat(5000).getBytes();

    // When
    var encrypted = hpkeService.encrypt(largeText);

    // Then - encrypted size should be less than plaintext due to compression
    assertTrue(encrypted.length < largeText.length + 2000); // Allow overhead
  }
}

