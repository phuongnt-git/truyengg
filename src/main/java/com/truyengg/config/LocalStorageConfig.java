package com.truyengg.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Web configuration for local storage - serves uploaded images as static resources.
 * Only enabled when storage.type=local.
 */
@Configuration
@ConditionalOnProperty(name = "truyengg.storage.type", havingValue = "local")
@Slf4j
public class LocalStorageConfig implements WebMvcConfigurer {

  @Value("${truyengg.storage.local.base-path:./uploads}")
  private String basePath;

  @Value("${truyengg.storage.local.serve-path:/uploads}")
  private String servePath;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    var resourceLocation = "file:" + basePath + "/";
    registry.addResourceHandler(servePath + "/**")
        .addResourceLocations(resourceLocation)
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
  }
}

