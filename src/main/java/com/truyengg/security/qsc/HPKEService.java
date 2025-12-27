package com.truyengg.security.qsc;

import com.truyengg.service.config.QSCSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class HPKEService {

  private final QSCKeyManager keyManager;
  private final QSCSettingsService qscSettings;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Encrypt plaintext using HPKE (Kyber1024 + AES-256-GCM)
   */
  public byte[] encrypt(byte[] plaintext) throws QSCException {
    var start = System.currentTimeMillis();

    try {
      // Compress if enabled and over threshold
      var shouldCompress = qscSettings.isCompressionEnabled() &&
          plaintext.length > qscSettings.getCompressionThreshold();
      var data = shouldCompress ? gzipCompress(plaintext) : plaintext;

      // Generate ephemeral AES-256 key
      var aesKey = generateRandomBytes(32);

      // Get Kyber public key
      var publicKeyInfo = keyManager.getCurrentKyberPublicKey();

      // Encapsulate AES key with Kyber (simplified - in production use proper KEM)
      var encapsulatedKey = encapsulateKeyWithKyber(publicKeyInfo.publicKeyBytes(), aesKey);

      // Encrypt with AES-256-GCM
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      var nonce = generateRandomBytes(12);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
      var ciphertext = cipher.doFinal(data);

      // Combine HPKE message
      var result = combineHPKEMessage(publicKeyInfo.id(), encapsulatedKey, nonce, ciphertext);

      logIfSlow(start, "encryption", plaintext.length);
      return result;

    } catch (Exception e) {
      log.error("[QSC] Encryption failed: size={}", plaintext.length, e);
      throw new QSCException("Encryption failed", e);
    }
  }

  /**
   * Decrypt HPKE ciphertext
   */
  public byte[] decrypt(byte[] hpkeMessage) throws QSCException {
    var start = System.currentTimeMillis();

    try {
      var parsed = parseHPKEMessage(hpkeMessage);

      // Get private key
      var privateKey = keyManager.getKyberPrivateKey(parsed.keyId());

      // Decapsulate to get AES key (simplified)
      var aesKey = decapsulateKeyWithKyber(privateKey, parsed.encapsulatedKey());

      // Decrypt with AES-256-GCM
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, parsed.nonce()));
      var plaintext = cipher.doFinal(parsed.ciphertext());

      // Decompress if needed
      var result = isGzipCompressed(plaintext) ? gzipDecompress(plaintext) : plaintext;

      logIfSlow(start, "decryption", hpkeMessage.length);
      return result;

    } catch (Exception e) {
      log.error("[QSC] Decryption failed: size={}", hpkeMessage.length, e);
      throw new QSCException("Decryption failed", e);
    }
  }

  // ===================================================================
  // HELPER METHODS
  // ===================================================================

  private byte[] encapsulateKeyWithKyber(byte[] kyberPublicKey, byte[] aesKey) {
    // Simplified: In production, use proper Kyber KEM from Bouncy Castle
    // For now, return placeholder that will be replaced with actual Kyber encapsulation
    var encapsulated = new byte[1568];  // Kyber1024 ciphertext size
    secureRandom.nextBytes(encapsulated);
    return encapsulated;
  }

  private byte[] decapsulateKeyWithKyber(java.security.PrivateKey kyberPrivateKey, byte[] encapsulatedKey) {
    // Simplified: In production, use proper Kyber KEM decapsulation
    // For now, return placeholder AES key
    var aesKey = new byte[32];
    secureRandom.nextBytes(aesKey);
    return aesKey;
  }

  private byte[] combineHPKEMessage(Long keyId, byte[] encapsulatedKey, byte[] nonce, byte[] ciphertext) {
    var keyIdBytes = new byte[8];
    ByteBuffer.wrap(keyIdBytes).putLong(keyId);

    var combined = new byte[8 + encapsulatedKey.length + nonce.length + ciphertext.length];
    var buffer = ByteBuffer.wrap(combined);
    buffer.put(keyIdBytes);
    buffer.put(encapsulatedKey);
    buffer.put(nonce);
    buffer.put(ciphertext);

    return combined;
  }

  private HPKEMessage parseHPKEMessage(byte[] data) {
    var buffer = ByteBuffer.wrap(data);

    var keyId = buffer.getLong();

    var encapsulatedKey = new byte[1568];  // Kyber1024 size
    buffer.get(encapsulatedKey);

    var nonce = new byte[12];
    buffer.get(nonce);

    var ciphertext = new byte[buffer.remaining()];
    buffer.get(ciphertext);

    return new HPKEMessage(keyId, encapsulatedKey, nonce, ciphertext);
  }

  private byte[] gzipCompress(byte[] data) throws Exception {
    var bos = new ByteArrayOutputStream();
    try (var gzip = new GZIPOutputStream(bos)) {
      gzip.write(data);
    }
    return bos.toByteArray();
  }

  private byte[] gzipDecompress(byte[] data) throws Exception {
    var bis = new ByteArrayInputStream(data);
    try (var gzip = new GZIPInputStream(bis)) {
      return gzip.readAllBytes();
    }
  }

  private boolean isGzipCompressed(byte[] data) {
    return data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
  }

  private byte[] generateRandomBytes(int length) {
    var bytes = new byte[length];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  private void logIfSlow(long start, String operation, int size) {
    var duration = System.currentTimeMillis() - start;
    if (qscSettings.shouldLogSlowOperations() && duration > qscSettings.getSlowThresholdMs()) {
      log.warn("[QSC] SLOW {}: {}ms for {} bytes", operation, duration, size);
    }
  }

  private record HPKEMessage(Long keyId, byte[] encapsulatedKey, byte[] nonce, byte[] ciphertext) {
  }
}

