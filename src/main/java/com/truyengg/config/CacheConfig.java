package com.truyengg.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.TimeUnit;

import static com.truyengg.domain.constant.AppConstants.AGGREGATED_STATS;
import static com.truyengg.domain.constant.AppConstants.AUTHENTICATION_CACHE;
import static com.truyengg.domain.constant.AppConstants.COMPLETED_JOBS;
import static com.truyengg.domain.constant.AppConstants.DASHBOARD_STATS;
import static com.truyengg.domain.constant.AppConstants.JOB_COUNTS;
import static com.truyengg.domain.constant.AppConstants.JOB_SETTINGS;
import static com.truyengg.domain.constant.AppConstants.REGISTRATION_CACHE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    var cacheManager = new CaffeineCacheManager() {
      @Override
      protected @NonNull Cache createCaffeineCache(String name) {
        // Check for GraphQL-specific caches with custom TTLs
        return switch (name) {
          // Dashboard stats - queried constantly, computed value, short TTL
          case DASHBOARD_STATS -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(10, SECONDS)
              .maximumSize(1)
              .recordStats()
              .build());

          // Total count for pagination - expensive COUNT(*), changes slowly
          case JOB_COUNTS -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(30, SECONDS)
              .maximumSize(1000)
              .recordStats()
              .build());

          // Aggregated stats - computed from all descendants
          case AGGREGATED_STATS -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(1, MINUTES)
              .maximumSize(500)
              .recordStats()
              .build());

          // Completed job details - immutable terminal states
          case COMPLETED_JOBS -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(5, MINUTES)
              .maximumSize(1000)
              .recordStats()
              .build());

          // Job settings - rarely changes
          case JOB_SETTINGS -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(5, MINUTES)
              .maximumSize(500)
              .recordStats()
              .build());

          // Passkey registration challenges - short TTL for security
          case REGISTRATION_CACHE -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(5, MINUTES)
              .maximumSize(100)
              .recordStats()
              .build());

          // Passkey authentication challenges - short TTL for security
          case AUTHENTICATION_CACHE -> new CaffeineCache(name, Caffeine.newBuilder()
              .expireAfterWrite(5, MINUTES)
              .maximumSize(100)
              .recordStats()
              .build());

          // Legacy dynamic duration support
          default -> {
            if (name.contains("#")) {
              yield createCacheWithDuration(name);
            }
            yield super.createCaffeineCache(name);
          }
        };
      }

      private Cache createCacheWithDuration(String fullName) {
        var parts = fullName.split("#");
        var cacheName = parts[0];
        var duration = parseDuration(parts[1]);
        var maxSize = getMaxSizeByPrefix(cacheName);

        var cache = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(duration.value(), duration.unit())
            .recordStats()
            .build();

        return new CaffeineCache(cacheName, cache);
      }

      private int getMaxSizeByPrefix(String cacheName) {
        if (!cacheName.contains(":")) return 1000;

        var prefix = cacheName.substring(0, cacheName.indexOf(":"));
        return switch (prefix) {
          case "img" -> 5000;                    // Images - large
          case "comic", "chapter" -> 2000;       // Content - medium
          case "ranking", "user", "comment", "api" -> 1000;  // Standard
          case "search" -> 500;                  // Search - small
          case "cfg", "qsc", "sec" -> 100;       // Config - tiny
          default -> 1000;
        };
      }

      private Duration parseDuration(String str) {
        var unit = Character.toLowerCase(str.charAt(str.length() - 1));
        var value = Long.parseLong(str.substring(0, str.length() - 1));

        return switch (unit) {
          case 's' -> new Duration(value, SECONDS);
          case 'm' -> new Duration(value, MINUTES);
          case 'h' -> new Duration(value, HOURS);
          case 'd' -> new Duration(value, DAYS);
          default -> {
            log.warn("Unknown duration unit '{}', defaulting to minutes", unit);
            yield new Duration(value, MINUTES);
          }
        };
      }
    };

    cacheManager.setCaffeine(defaultCaffeineBuilder());
    return cacheManager;
  }

  private Caffeine<Object, Object> defaultCaffeineBuilder() {
    return Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, MINUTES)
        .recordStats();
  }

  private record Duration(long value, TimeUnit unit) {
  }
}


