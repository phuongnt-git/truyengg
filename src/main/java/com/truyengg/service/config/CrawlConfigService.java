package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlConfigService {

  private static final String BASE = "features.crawl.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public int getMaxRetries() {
    return settingService.getIntValue(BASE + "max_retries", 3);
  }

  @Cacheable("cfg:settings#10m")
  public int getRetryDelay() {
    return settingService.getIntValue(BASE + "retry_delay", 2);
  }

  @Cacheable("cfg:settings#10m")
  public int getRequestTimeout() {
    return settingService.getIntValue(BASE + "request_timeout", 15);
  }

  @Cacheable("cfg:settings#10m")
  public int getPerAdminLimit() {
    return settingService.getIntValue(BASE + "per_admin_limit", 5);
  }

  @Cacheable("cfg:settings#10m")
  public int getPerServerLimit() {
    return settingService.getIntValue(BASE + "per_server_limit", 25);
  }

  @Cacheable("cfg:settings#10m")
  public String getQueueCron() {
    return settingService.getStringValue(BASE + "queue_cron", "0 */5 * * * *");
  }
}

