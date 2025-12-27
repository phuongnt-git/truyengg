package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service("qscSettingsService")
@RequiredArgsConstructor
public class QSCSettingsService {

  private static final String BASE = "security.qsc.";
  private final SettingService settingService;

  @Cacheable(value = "qsc:settings#10m", key = "'hpke.enabled'")
  public boolean isHPKEEnabled() {
    return settingService.getBooleanValue(BASE + "hpke.enabled", false);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'hpke.kem_algorithm'")
  public String getKemAlgorithm() {
    return settingService.getStringValue(BASE + "hpke.kem_algorithm", "KYBER1024");
  }

  @Cacheable(value = "qsc:settings#10m", key = "'hpke.compression_enabled'")
  public boolean isCompressionEnabled() {
    return settingService.getBooleanValue(BASE + "hpke.compression_enabled", true);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'hpke.compression_threshold'")
  public int getCompressionThreshold() {
    return settingService.getIntValue(BASE + "hpke.compression_threshold", 1024);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'jwt.access_token_algorithm'")
  public String getAccessTokenAlgorithm() {
    return settingService.getStringValue(BASE + "jwt.access_token_algorithm", "DILITHIUM3");
  }

  @Cacheable(value = "qsc:settings#10m", key = "'jwt.refresh_token_algorithm'")
  public String getRefreshTokenAlgorithm() {
    return settingService.getStringValue(BASE + "jwt.refresh_token_algorithm", "DILITHIUM3");
  }

  @Cacheable(value = "qsc:settings#10m", key = "'keys.rotation_enabled'")
  public boolean isKeyRotationEnabled() {
    return settingService.getBooleanValue(BASE + "keys.rotation_enabled", true);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'keys.rotation_interval'")
  public int getKeyRotationInterval() {
    return settingService.getIntValue(BASE + "keys.rotation_interval", 86400);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'keys.rotation_overlap'")
  public int getKeyRotationOverlap() {
    return settingService.getIntValue(BASE + "keys.rotation_overlap", 3600);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'keys.retention_days'")
  public int getKeyRetentionDays() {
    return settingService.getIntValue(BASE + "keys.retention_days", 30);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'keys.auto_generate_on_startup'")
  public boolean shouldAutoGenerateKeys() {
    return settingService.getBooleanValue(BASE + "keys.auto_generate_on_startup", true);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'performance.websocket_enabled'")
  public boolean isWebSocketEncryptionEnabled() {
    return settingService.getBooleanValue(BASE + "performance.websocket_enabled", true);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'performance.websocket_threshold'")
  public int getWebSocketThreshold() {
    return settingService.getIntValue(BASE + "performance.websocket_threshold", 256);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'performance.async_threshold'")
  public int getAsyncThreshold() {
    return settingService.getIntValue(BASE + "performance.async_threshold", 1048576);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'performance.log_slow_ops'")
  public boolean shouldLogSlowOperations() {
    return settingService.getBooleanValue(BASE + "performance.log_slow_ops", true);
  }

  @Cacheable(value = "qsc:settings#10m", key = "'performance.slow_threshold_ms'")
  public int getSlowThresholdMs() {
    return settingService.getIntValue(BASE + "performance.slow_threshold_ms", 100);
  }

  @CacheEvict(value = "qsc:settings#10m", allEntries = true)
  public void updateSetting(String key, String value, Long userId) {
    settingService.updateSetting(BASE + key, value, userId);
  }

  @CacheEvict(value = "qsc:settings#10m", allEntries = true)
  public void enableHPKE(boolean enabled, Long userId) {
    settingService.updateSetting(BASE + "hpke.enabled", String.valueOf(enabled), userId);
  }
}
