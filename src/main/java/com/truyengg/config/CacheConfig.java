package com.truyengg.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

  @Value("${truyengg.image.cache.max-size:1000}")
  private int imageCacheMaxSize;

  @Value("${truyengg.image.cache.expire-after-write-hours:24}")
  private int imageCacheExpireAfterWriteHours;

  @Value("${truyengg.image.cache.expire-after-access-hours:1}")
  private int imageCacheExpireAfterAccessHours;

  @Bean
  public CacheManager cacheManager() {
    var cacheManager = new CaffeineCacheManager() {
      @Override
      protected Cache createCaffeineCache(String name) {
        // Custom configuration for image caches
        if ("imageCache".equals(name) || "originalImageCache".equals(name)) {
          var cache = Caffeine.newBuilder()
              .maximumSize(imageCacheMaxSize)
              .expireAfterWrite(imageCacheExpireAfterWriteHours, TimeUnit.HOURS)
              .expireAfterAccess(imageCacheExpireAfterAccessHours, TimeUnit.HOURS)
              .recordStats()
              .build();
          return new CaffeineCache(name, cache);
        }
        // Default configuration for other caches
        return super.createCaffeineCache(name);
      }
    };
    cacheManager.setCaffeine(defaultCaffeineBuilder());
    return cacheManager;
  }

  private Caffeine<Object, Object> defaultCaffeineBuilder() {
    return Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats();
  }
}

