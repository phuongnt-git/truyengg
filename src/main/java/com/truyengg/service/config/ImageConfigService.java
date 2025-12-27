package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageConfigService {

  private static final String BASE = "storage.cache.image.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public int getCacheMaxSize() {
    return settingService.getIntValue(BASE + "max_size", 1000);
  }

  @Cacheable("cfg:settings#10m")
  public int getCacheExpireWriteHours() {
    return settingService.getIntValue(BASE + "expire_write_hours", 24);
  }

  @Cacheable("cfg:settings#10m")
  public int getCacheExpireAccessHours() {
    return settingService.getIntValue(BASE + "expire_access_hours", 1);
  }

  @Cacheable("cfg:settings#10m")
  public boolean isCompressionEnabled() {
    return settingService.getBooleanValue(BASE + "compression_enabled", true);
  }

  @Cacheable("cfg:settings#10m")
  public double getJpegQuality() {
    return settingService.getDoubleValue(BASE + "jpeg_quality", 0.90);
  }

  @Cacheable("cfg:settings#10m")
  public boolean shouldRemoveMetadata() {
    return settingService.getBooleanValue(BASE + "remove_metadata", true);
  }
}

