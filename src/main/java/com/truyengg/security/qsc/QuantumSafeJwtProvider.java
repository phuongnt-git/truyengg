package com.truyengg.security.qsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.enums.KeyUsage;
import com.truyengg.service.config.JwtConfigService;
import com.truyengg.service.config.QSCSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuantumSafeJwtProvider {

  private final QSCKeyManager keyManager;
  private final QSCAlgorithmRegistry algorithmRegistry;
  private final JwtConfigService jwtConfig;
  private final QSCSettingsService qscSettings;
  private final ObjectMapper objectMapper;

  // ===================================================================
  // ACCESS TOKEN
  // ===================================================================

  public String generateAccessToken(Long userId, String email, String role) {
    if (!qscSettings.isHPKEEnabled()) {
      return generateClassicalToken(userId, email, role, "access", jwtConfig.getAccessTokenExpiration());
    }

    var keyInfo = keyManager.getDilithiumPublicKeyForAccess();

    var header = Map.of(
        "alg", "DILITHIUM3",
        "typ", "JWT",
        "kid", keyInfo.id().toString()
    );

    var now = Instant.now();
    var payload = Map.of(
        "sub", userId.toString(),
        "email", email,
        "role", role,
        "type", "access",
        "iat", now.getEpochSecond(),
        "exp", now.plusMillis(jwtConfig.getAccessTokenExpiration()).getEpochSecond(),
        "nonce", UUID.randomUUID().toString()
    );

    var message = base64UrlEncode(toJson(header)) + "." + base64UrlEncode(toJson(payload));
    var signature = signWithDilithium(message.getBytes(UTF_8), KeyUsage.JWT_ACCESS);

    return message + "." + base64UrlEncode(signature);
  }

  // ===================================================================
  // REFRESH TOKEN
  // ===================================================================

  public String generateRefreshToken(Long userId, String email, String role) {
    if (!qscSettings.isHPKEEnabled()) {
      return generateClassicalToken(userId, email, role, "refresh", jwtConfig.getRefreshTokenExpiration());
    }

    var keyInfo = keyManager.getDilithiumPublicKeyForRefresh();

    var header = Map.of(
        "alg", "DILITHIUM3",
        "typ", "JWT",
        "kid", keyInfo.id().toString()
    );

    var now = Instant.now();
    var jti = UUID.randomUUID().toString();

    var payload = Map.of(
        "sub", userId.toString(),
        "email", email,
        "role", role,
        "type", "refresh",
        "jti", jti,
        "iat", now.getEpochSecond(),
        "exp", now.plusMillis(jwtConfig.getRefreshTokenExpiration()).getEpochSecond(),
        "nonce", UUID.randomUUID().toString()
    );

    var message = base64UrlEncode(toJson(header)) + "." + base64UrlEncode(toJson(payload));
    var signature = signWithDilithium(message.getBytes(UTF_8), KeyUsage.JWT_REFRESH);

    return message + "." + base64UrlEncode(signature);
  }

  // ===================================================================
  // VALIDATION
  // ===================================================================

  public boolean validateToken(String token) {
    try {
      var parts = token.split("\\.");
      if (parts.length != 3) return false;

      var header = parseJson(base64UrlDecode(parts[0]));
      var payload = parseJson(base64UrlDecode(parts[1]));
      var algorithm = (String) header.get("alg");
      var message = parts[0] + "." + parts[1];
      var signature = base64UrlDecodeBytes(parts[2]);

      // Check expiration
      var exp = ((Number) payload.get("exp")).longValue();
      if (Instant.now().getEpochSecond() > exp) {
        return false;
      }

      // Verify based on algorithm
      return switch (algorithm) {
        case "DILITHIUM2", "DILITHIUM3", "DILITHIUM5" -> {
          var tokenType = "refresh".equals(payload.get("type")) ? KeyUsage.JWT_REFRESH : KeyUsage.JWT_ACCESS;
          yield verifyDilithiumSignature(message.getBytes(UTF_8), signature, tokenType);
        }
        case "HS256", "HS512" -> verifyHMACSignature(message.getBytes(UTF_8), signature);
        default -> false;
      };

    } catch (Exception e) {
      log.error("[QSC] Token validation failed", e);
      return false;
    }
  }

  public String getJti(String token) {
    try {
      var payload = parseJson(base64UrlDecode(token.split("\\.")[1]));
      return (String) payload.get("jti");
    } catch (Exception e) {
      return null;
    }
  }

  public Long getUserIdFromToken(String token) {
    try {
      var payload = parseJson(base64UrlDecode(token.split("\\.")[1]));
      return Long.parseLong((String) payload.get("sub"));
    } catch (Exception e) {
      return null;
    }
  }

  // ===================================================================
  // CRYPTO OPERATIONS
  // ===================================================================

  private byte[] signWithDilithium(byte[] data, KeyUsage tokenType) {
    try {
      var algorithm = tokenType == KeyUsage.JWT_ACCESS
          ? qscSettings.getAccessTokenAlgorithm()
          : qscSettings.getRefreshTokenAlgorithm();

      var provider = algorithmRegistry.getSignatureProvider(algorithm);
      var privateKey = keyManager.getCurrentDilithiumPrivateKey(tokenType);

      return provider.sign(privateKey, data);

    } catch (Exception e) {
      throw new QSCException("Dilithium signing failed", e);
    }
  }

  private boolean verifyDilithiumSignature(byte[] data, byte[] signature, KeyUsage tokenType) {
    try {
      var algorithm = tokenType == KeyUsage.JWT_ACCESS
          ? qscSettings.getAccessTokenAlgorithm()
          : qscSettings.getRefreshTokenAlgorithm();

      var provider = algorithmRegistry.getSignatureProvider(algorithm);
      var publicKey = tokenType == KeyUsage.JWT_ACCESS
          ? keyManager.getDilithiumPublicKeyForAccess()
          : keyManager.getDilithiumPublicKeyForRefresh();

      return provider.verify(publicKey.publicKey(), data, signature);

    } catch (Exception e) {
      log.error("[QSC] Dilithium verification failed", e);
      return false;
    }
  }

  private byte[] signWithHMAC(String message) {
    try {
      var secret = jwtConfig.getSecret();
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
      return mac.doFinal(message.getBytes(UTF_8));
    } catch (Exception e) {
      throw new QSCException("HMAC signing failed", e);
    }
  }

  private boolean verifyHMACSignature(byte[] message, byte[] signature) {
    try {
      var expected = signWithHMAC(new String(message, UTF_8));
      return MessageDigest.isEqual(signature, expected);
    } catch (Exception e) {
      return false;
    }
  }

  // Classical token generation
  private String generateClassicalToken(Long userId, String email, String role, String type, long expiration) {
    var header = Map.of("alg", "HS256", "typ", "JWT");

    var now = Instant.now();
    var payload = Map.of(
        "sub", userId.toString(),
        "email", email,
        "role", role,
        "type", type,
        "iat", now.getEpochSecond(),
        "exp", now.plusMillis(expiration).getEpochSecond()
    );

    if ("refresh".equals(type)) {
      payload = Map.of(
          "sub", userId.toString(),
          "email", email,
          "role", role,
          "type", type,
          "jti", UUID.randomUUID().toString(),
          "iat", now.getEpochSecond(),
          "exp", now.plusMillis(expiration).getEpochSecond()
      );
    }

    var message = base64UrlEncode(toJson(header)) + "." + base64UrlEncode(toJson(payload));
    var signature = signWithHMAC(message);

    return message + "." + base64UrlEncode(signature);
  }

  // ===================================================================
  // HELPERS
  // ===================================================================

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new QSCException("JSON serialization failed", e);
    }
  }

  private Map<String, Object> parseJson(String json) {
    try {
      return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
      });
    } catch (Exception e) {
      throw new QSCException("JSON parsing failed", e);
    }
  }

  private String base64UrlEncode(byte[] data) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  private String base64UrlEncode(String data) {
    return base64UrlEncode(data.getBytes(UTF_8));
  }

  private String base64UrlDecode(String encoded) {
    return new String(base64UrlDecodeBytes(encoded), UTF_8);
  }

  private byte[] base64UrlDecodeBytes(String encoded) {
    return Base64.getUrlDecoder().decode(encoded);
  }
}

