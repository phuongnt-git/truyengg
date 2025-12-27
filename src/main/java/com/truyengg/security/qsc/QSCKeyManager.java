package com.truyengg.security.qsc;

import com.truyengg.domain.entity.QSCKeyPairEntity;
import com.truyengg.domain.enums.KeyType;
import com.truyengg.domain.enums.KeyUsage;
import com.truyengg.domain.repository.QSCKeyPairRepository;
import com.truyengg.security.qsc.model.DilithiumPublicKeyInfo;
import com.truyengg.security.qsc.model.KyberPublicKeyInfo;
import com.truyengg.service.config.QSCSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZonedDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class QSCKeyManager {

  private final QSCKeyPairRepository keyPairRepository;
  private final QSCSettingsService qscSettings;

  @PostConstruct
  public void initialize() {
    Security.addProvider(new BouncyCastlePQCProvider());
    log.info("[QSC] Initialized Bouncy Castle PQC Provider");

    if (qscSettings.shouldAutoGenerateKeys()) {
      ensureKeysExist();
    }
  }

  private void ensureKeysExist() {
    var kyberExists = keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.KYBER, KeyUsage.HPKE)
        .isPresent();

    var accessKeyExists = keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.DILITHIUM, KeyUsage.JWT_ACCESS)
        .isPresent();

    var refreshKeyExists = keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.DILITHIUM, KeyUsage.JWT_REFRESH)
        .isPresent();

    if (!kyberExists) {
      log.info("[QSC] Generating initial Kyber key...");
      generateKyberKeyPair(KeyUsage.HPKE);
    }

    if (!accessKeyExists) {
      log.info("[QSC] Generating initial Dilithium key for access tokens...");
      generateDilithiumKeyPair(KeyUsage.JWT_ACCESS);
    }

    if (!refreshKeyExists) {
      log.info("[QSC] Generating initial Dilithium key for refresh tokens...");
      generateDilithiumKeyPair(KeyUsage.JWT_REFRESH);
    }
  }

  // ===================================================================
  // PUBLIC KEY RETRIEVAL
  // ===================================================================

  @Cacheable(value = "qsc:publicKeys#24h", key = "'kyber'")
  public KyberPublicKeyInfo getCurrentKyberPublicKey() {
    log.debug("[QSC] Loading current Kyber public key");

    return keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.KYBER, KeyUsage.HPKE)
        .map(this::toKyberPublicKeyInfo)
        .orElseThrow(() -> new QSCException("No active Kyber key found"));
  }

  @Cacheable(value = "qsc:publicKeys#24h", key = "'dilithium-access'")
  public DilithiumPublicKeyInfo getDilithiumPublicKeyForAccess() {
    return getDilithiumKeyByUsage(KeyUsage.JWT_ACCESS);
  }

  @Cacheable(value = "qsc:publicKeys#24h", key = "'dilithium-refresh'")
  public DilithiumPublicKeyInfo getDilithiumPublicKeyForRefresh() {
    return getDilithiumKeyByUsage(KeyUsage.JWT_REFRESH);
  }

  private DilithiumPublicKeyInfo getDilithiumKeyByUsage(KeyUsage usage) {
    return keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.DILITHIUM, usage)
        .map(this::toDilithiumPublicKeyInfo)
        .orElseThrow(() -> new QSCException("No active Dilithium key for usage: " + usage));
  }

  // ===================================================================
  // PRIVATE KEY RETRIEVAL
  // ===================================================================

  @Cacheable(value = "qsc:privateKeys#24h", key = "'kyber-' + #keyId")
  public PrivateKey getKyberPrivateKey(Long keyId) {
    return keyPairRepository.findById(keyId)
        .filter(k -> k.getKeyType() == KeyType.KYBER)
        .map(this::parseKyberPrivateKey)
        .orElseThrow(() -> new QSCException("Kyber key not found: " + keyId));
  }

  @Cacheable(value = "qsc:privateKeys#24h", key = "'dilithium-' + #usage")
  public PrivateKey getCurrentDilithiumPrivateKey(KeyUsage usage) {
    return keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.DILITHIUM, usage)
        .map(this::parseDilithiumPrivateKey)
        .orElseThrow(() -> new QSCException("No Dilithium key for usage: " + usage));
  }

  // ===================================================================
  // KEY ROTATION
  // ===================================================================

  @CacheEvict(value = {"qsc:publicKeys#24h", "qsc:privateKeys#24h"}, allEntries = true)
  @Scheduled(fixedRateString = "#{@qscSettingsService.getKeyRotationInterval() * 1000}")
  public void rotateKeys() {
    if (!qscSettings.isKeyRotationEnabled()) {
      log.debug("[QSC] Key rotation disabled");
      return;
    }

    if (!shouldRotateKeys()) {
      log.debug("[QSC] Keys are still valid, skipping rotation");
      return;
    }

    log.info("[QSC] Starting key rotation...");

    try {
      deactivateOldKeys(KeyType.KYBER, KeyUsage.HPKE);
      deactivateOldKeys(KeyType.DILITHIUM, KeyUsage.JWT_ACCESS);
      deactivateOldKeys(KeyType.DILITHIUM, KeyUsage.JWT_REFRESH);

      var newKyber = generateKyberKeyPair(KeyUsage.HPKE);
      var newAccessKey = generateDilithiumKeyPair(KeyUsage.JWT_ACCESS);
      var newRefreshKey = generateDilithiumKeyPair(KeyUsage.JWT_REFRESH);

      cleanupExpiredKeys(qscSettings.getKeyRetentionDays());

      log.info("[QSC] Rotation completed: Kyber={}, Access={}, Refresh={}",
          newKyber.id(), newAccessKey.id(), newRefreshKey.id());

    } catch (Exception e) {
      log.error("[QSC] KEY ROTATION FAILED", e);
      throw new QSCException("Key rotation failed", e);
    }
  }

  private boolean shouldRotateKeys() {
    var currentKey = keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.KYBER, KeyUsage.HPKE)
        .orElse(null);

    if (currentKey == null) {
      return true;
    }

    var rotationThreshold = ZonedDateTime.now().plusSeconds(qscSettings.getKeyRotationOverlap());
    return currentKey.getExpiresAt().isBefore(rotationThreshold);
  }

  private void deactivateOldKeys(KeyType keyType, KeyUsage keyUsage) {
    keyPairRepository.findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(keyType, keyUsage)
        .ifPresent(key -> {
          key.setActive(false);
          keyPairRepository.save(key);
          log.debug("[QSC] Deactivated old key: id={}, type={}, usage={}", key.getId(), keyType, keyUsage);
        });
  }

  private void cleanupExpiredKeys(int retentionDays) {
    var cutoffDate = ZonedDateTime.now().minusDays(retentionDays);
    var deleted = keyPairRepository.deleteExpiredKeys(cutoffDate);
    if (deleted > 0) {
      log.info("[QSC] Cleaned up {} expired keys", deleted);
    }
  }

  // ===================================================================
  // KEY GENERATION
  // ===================================================================

  private KyberPublicKeyInfo generateKyberKeyPair(KeyUsage usage) {
    try {
      var algorithm = qscSettings.getKemAlgorithm();
      var keyGen = KeyPairGenerator.getInstance(algorithm, "BCPQC");
      var keyPair = keyGen.generateKeyPair();

      var entity = QSCKeyPairEntity.builder()
          .keyType(KeyType.KYBER)
          .algorithm(algorithm)
          .keyUsage(usage)
          .publicKey(keyPair.getPublic().getEncoded())
          .privateKey(keyPair.getPrivate().getEncoded())  // TODO: Encrypt with Jasypt
          .fingerprint(calculateFingerprint(keyPair.getPublic().getEncoded()))
          .createdAt(ZonedDateTime.now())
          .expiresAt(ZonedDateTime.now().plusSeconds(qscSettings.getKeyRotationInterval()))
          .isActive(true)
          .rotationCount(0)
          .build();

      var saved = keyPairRepository.save(entity);
      log.info("[QSC] Generated Kyber key: id={}, usage={}, algorithm={}", saved.getId(), usage, algorithm);

      return toKyberPublicKeyInfo(saved);

    } catch (Exception e) {
      log.error("[QSC] Failed to generate Kyber key", e);
      throw new QSCException("Kyber key generation failed", e);
    }
  }

  private DilithiumPublicKeyInfo generateDilithiumKeyPair(KeyUsage usage) {
    try {
      var algorithm = usage == KeyUsage.JWT_ACCESS
          ? qscSettings.getAccessTokenAlgorithm()
          : qscSettings.getRefreshTokenAlgorithm();

      var keyGen = KeyPairGenerator.getInstance(algorithm, "BCPQC");
      var keyPair = keyGen.generateKeyPair();

      var entity = QSCKeyPairEntity.builder()
          .keyType(KeyType.DILITHIUM)
          .algorithm(algorithm)
          .keyUsage(usage)
          .publicKey(keyPair.getPublic().getEncoded())
          .privateKey(keyPair.getPrivate().getEncoded())  // TODO: Encrypt with Jasypt
          .fingerprint(calculateFingerprint(keyPair.getPublic().getEncoded()))
          .createdAt(ZonedDateTime.now())
          .expiresAt(ZonedDateTime.now().plusSeconds(qscSettings.getKeyRotationInterval()))
          .isActive(true)
          .rotationCount(0)
          .build();

      var saved = keyPairRepository.save(entity);
      log.info("[QSC] Generated Dilithium key: id={}, usage={}, algorithm={}", saved.getId(), usage, algorithm);

      return toDilithiumPublicKeyInfo(saved);

    } catch (Exception e) {
      log.error("[QSC] Failed to generate Dilithium key", e);
      throw new QSCException("Dilithium key generation failed", e);
    }
  }

  // ===================================================================
  // HELPER METHODS
  // ===================================================================

  private KyberPublicKeyInfo toKyberPublicKeyInfo(QSCKeyPairEntity entity) {
    return KyberPublicKeyInfo.builder()
        .id(entity.getId())
        .algorithm(entity.getAlgorithm())
        .publicKeyBytes(entity.getPublicKey())
        .fingerprint(entity.getFingerprint())
        .expiresAt(entity.getExpiresAt())
        .build();
  }

  private DilithiumPublicKeyInfo toDilithiumPublicKeyInfo(QSCKeyPairEntity entity) {
    try {
      var publicKey = parsePublicKey(entity.getPublicKey(), entity.getAlgorithm());

      return DilithiumPublicKeyInfo.builder()
          .id(entity.getId())
          .algorithm(entity.getAlgorithm())
          .publicKeyBytes(entity.getPublicKey())
          .publicKey(publicKey)
          .fingerprint(entity.getFingerprint())
          .expiresAt(entity.getExpiresAt())
          .build();

    } catch (Exception e) {
      throw new QSCException("Failed to parse Dilithium public key", e);
    }
  }

  private PrivateKey parseKyberPrivateKey(QSCKeyPairEntity entity) {
    try {
      var keyFactory = KeyFactory.getInstance(entity.getAlgorithm(), "BCPQC");
      var keySpec = new PKCS8EncodedKeySpec(entity.getPrivateKey());
      return keyFactory.generatePrivate(keySpec);
    } catch (Exception e) {
      throw new QSCException("Failed to parse Kyber private key", e);
    }
  }

  private PrivateKey parseDilithiumPrivateKey(QSCKeyPairEntity entity) {
    try {
      var keyFactory = KeyFactory.getInstance(entity.getAlgorithm(), "BCPQC");
      var keySpec = new PKCS8EncodedKeySpec(entity.getPrivateKey());
      return keyFactory.generatePrivate(keySpec);
    } catch (Exception e) {
      throw new QSCException("Failed to parse Dilithium private key", e);
    }
  }

  private PublicKey parsePublicKey(byte[] publicKeyBytes, String algorithm) throws Exception {
    var keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
    var keySpec = new X509EncodedKeySpec(publicKeyBytes);
    return keyFactory.generatePublic(keySpec);
  }

  private String calculateFingerprint(byte[] publicKey) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var hash = digest.digest(publicKey);
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      log.error("[QSC] Failed to calculate fingerprint", e);
      return "";
    }
  }

  public ZonedDateTime getNextRotationTime() {
    var currentKey = keyPairRepository
        .findTopByKeyTypeAndKeyUsageAndIsActiveTrueOrderByCreatedAtDesc(KeyType.KYBER, KeyUsage.HPKE)
        .orElse(null);

    if (currentKey == null) {
      return ZonedDateTime.now();
    }

    return currentKey.getCreatedAt().plusSeconds(qscSettings.getKeyRotationInterval());
  }
}

