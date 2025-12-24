package com.truyengg.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class OAuth2Config {

  @Value("${truyengg.oauth2.google.client-id:}")
  private String clientId;

  @Value("${truyengg.oauth2.google.client-secret:}")
  private String clientSecret;

  @Value("${truyengg.oauth2.google.redirect-uri:}")
  private String redirectUri;
}
