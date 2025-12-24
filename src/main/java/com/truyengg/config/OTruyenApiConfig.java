package com.truyengg.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "truyengg.otruyen")
@Getter
@Setter
public class OTruyenApiConfig {
  private String baseUrl = "https://otruyenapi.com/v1/api";
  private String cdnImage = "https://img.otruyenapi.com";
}

