package com.truyengg.security.qsc;

import com.truyengg.domain.enums.KeyType;
import com.truyengg.domain.enums.KeyUsage;
import com.truyengg.domain.repository.QSCKeyPairRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class QSCKeyManagerTest {

  @Autowired
  private QSCKeyManager keyManager;

  @Autowired
  private QSCKeyPairRepository keyPairRepository;

  @Test
  void shouldGenerateKyberKeyPair() {
    // When
    var keyInfo = keyManager.getCurrentKyberPublicKey();

    // Then
    assertNotNull(keyInfo);
    assertEquals("KYBER1024", keyInfo.algorithm());
    assertNotNull(keyInfo.publicKeyBytes());
    assertTrue(keyInfo.publicKeyBytes().length > 0);
  }

  @Test
  void shouldGenerateDilithiumKeysForAccessAndRefresh() {
    // When
    var accessKey = keyManager.getDilithiumPublicKeyForAccess();
    var refreshKey = keyManager.getDilithiumPublicKeyForRefresh();

    // Then
    assertNotNull(accessKey);
    assertNotNull(refreshKey);
    assertNotEquals(accessKey.id(), refreshKey.id());
  }

  @Test
  void shouldCacheKeys() {
    // First call - loads from DB
    var key1 = keyManager.getCurrentKyberPublicKey();

    // Second call - from cache
    var key2 = keyManager.getCurrentKyberPublicKey();

    // Then
    assertEquals(key1.id(), key2.id());
  }

  @Test
  void shouldRotateKeys() {
    // Given
    var oldKyber = keyManager.getCurrentKyberPublicKey();

    // When
    keyManager.rotateKeys();

    // Then
    var newKyber = keyManager.getCurrentKyberPublicKey();
    assertNotEquals(oldKyber.id(), newKyber.id());
  }
}

