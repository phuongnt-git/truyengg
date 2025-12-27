package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtConfigService {

  private static final String BASE = "security.jwt.tokens.";
  private static final String REFRESH_BASE = "security.jwt.refresh.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public String getSecret() {
    return settingService.getStringValue(BASE + "secret", "your-secret-key-change-in-production-min-256-bits");
  }

  @Cacheable("cfg:settings#10m")
  public long getAccessTokenExpiration() {
    return settingService.getLongValue(BASE + "access_expiration", 3600000L);
  }

  @Cacheable("cfg:settings#10m")
  public long getRefreshTokenExpiration() {
    return settingService.getLongValue(REFRESH_BASE + "expiration", 604800000L);
  }

  @Cacheable("cfg:settings#10m")
  public int getMaxRefreshTokensPerUser() {
    return settingService.getIntValue(REFRESH_BASE + "max_per_user", 5);
  }

  @Cacheable("cfg:settings#10m")
  public int getRefreshTokenCookieMaxAge() {
    return settingService.getIntValue(REFRESH_BASE + "cookie_max_age", 604800);
  }
}

