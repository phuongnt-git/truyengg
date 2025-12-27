package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinIOConfigService {

  private static final String BASE = "storage.minio.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public String getEndpoint() {
    return settingService.getStringValue(BASE + "endpoint", "http://localhost:9000");
  }

  @Cacheable("cfg:settings#10m")
  public String getAccessKey() {
    return settingService.getStringValue(BASE + "access_key", "truyengg");
  }

  @Cacheable("cfg:settings#10m")
  public String getSecretKey() {
    return settingService.getStringValue(BASE + "secret_key", "truyengg");
  }

  @Cacheable("cfg:settings#10m")
  public String getBucketName() {
    return settingService.getStringValue(BASE + "bucket_name", "truyengg");
  }
}

