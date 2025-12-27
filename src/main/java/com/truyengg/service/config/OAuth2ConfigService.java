package com.truyengg.service.config;

import com.truyengg.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuth2ConfigService {

  private static final String BASE = "integrations.oauth2.google.";
  private final SettingService settingService;

  @Cacheable("cfg:settings#10m")
  public String getGoogleClientId() {
    return settingService.getStringValue(BASE + "client_id", "");
  }

  @Cacheable("cfg:settings#10m")
  public String getGoogleClientSecret() {
    return settingService.getStringValue(BASE + "client_secret", "");
  }

  @Cacheable("cfg:settings#10m")
  public String getGoogleRedirectUri() {
    return settingService.getStringValue(BASE + "redirect_uri", "http://localhost:8080/auth/google/callback");
  }
}

