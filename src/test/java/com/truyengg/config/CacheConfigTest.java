package com.truyengg.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CacheConfigTest {

  @Autowired
  private CacheManager cacheManager;

  @Test
  void shouldParseDurationFromCacheName() {
    // Given
    var cacheName = "cfg:settings#10m";

    // When
    var cache = cacheManager.getCache(cacheName);

    // Then
    assertNotNull(cache);
  }

  @Test
  void shouldSupportVariousDurationUnits() {
    assertCacheCreated("test:cache#60s");   // Seconds
    assertCacheCreated("test:cache#10m");   // Minutes
    assertCacheCreated("test:cache#24h");   // Hours
    assertCacheCreated("test:cache#7d");    // Days
  }

  @Test
  void shouldIsolatePrefixedCaches() {
    // Given
    var qscCache = cacheManager.getCache("qsc:settings#10m");
    var cfgCache = cacheManager.getCache("cfg:settings#10m");

    // When
    qscCache.put("key1", "value1");
    cfgCache.put("key1", "value2");

    // Then
    assertEquals("value1", qscCache.get("key1", String.class));
    assertEquals("value2", cfgCache.get("key1", String.class));
  }

  private void assertCacheCreated(String cacheName) {
    var cache = cacheManager.getCache(cacheName);
    assertNotNull(cache, "Cache should be created: " + cacheName);
  }
}

