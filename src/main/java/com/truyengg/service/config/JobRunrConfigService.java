package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobRunrConfigService {

  private static final String BASE = "jobs.jobrunr.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public boolean isDashboardEnabled() {
    return settingService.getBooleanValue(BASE + "dashboard_enabled", true);
  }

  @Cacheable("cfg:settings#10m")
  public String getDashboardUsername() {
    return settingService.getStringValue(BASE + "dashboard_username", "truyengg");
  }

  @Cacheable("cfg:settings#10m")
  public String getDashboardPassword() {
    return settingService.getStringValue(BASE + "dashboard_password", "truyengg");
  }

  @Cacheable("cfg:settings#10m")
  public int getWorkerCount() {
    return settingService.getIntValue(BASE + "worker_count", 5);
  }
}

