package com.truyengg.controller.api;

import com.truyengg.model.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Tag(name = "Admin Cache", description = "Cache monitoring and management")
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminCacheController {

  private final CacheManager cacheManager;

  @GetMapping("/stats")
  @Operation(summary = "Get cache stats", description = "Get all cache statistics with optional filter/search")
  public ResponseEntity<ApiResponse<List<CacheStatsResponse>>> getAllCacheStats(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String search) {

    var allStats = new ArrayList<CacheStatsResponse>();

    for (String cacheName : cacheManager.getCacheNames()) {
      var cacheCategory = getCacheCategory(cacheName);

      if (category != null && !cacheCategory.equalsIgnoreCase(category)) {
        continue;
      }

      if (search != null && !cacheName.toLowerCase().contains(search.toLowerCase())) {
        continue;
      }

      var cache = cacheManager.getCache(cacheName);
      if (cache instanceof CaffeineCache caffeineCache) {
        var nativeCache = caffeineCache.getNativeCache();
        var stats = nativeCache.stats();

        allStats.add(CacheStatsResponse.builder()
            .cacheName(cacheName)
            .displayName(getDisplayName(cacheName))
            .category(cacheCategory)
            .hitCount(stats.hitCount())
            .missCount(stats.missCount())
            .hitRate(stats.hitRate())
            .hitRatePercent(String.format("%.2f%%", stats.hitRate() * 100))
            .evictionCount(stats.evictionCount())
            .currentSize(nativeCache.estimatedSize())
            .ttl(parseDurationFromName(cacheName))
            .build());
      }
    }

    allStats.sort(Comparator
        .comparing(CacheStatsResponse::category)
        .thenComparing(CacheStatsResponse::displayName));

    return ResponseEntity.ok(ApiResponse.success(allStats));
  }

  @GetMapping("/categories")
  @Operation(summary = "Get categories", description = "Get all cache categories")
  public ResponseEntity<ApiResponse<List<String>>> getCacheCategories() {
    var categories = cacheManager.getCacheNames().stream()
        .map(this::getCacheCategory)
        .distinct()
        .sorted()
        .toList();

    return ResponseEntity.ok(ApiResponse.success(categories));
  }

  @PostMapping("/clear/{cacheName}")
  @Operation(summary = "Clear cache", description = "Clear a specific cache")
  public ResponseEntity<ApiResponse<Object>> clearCache(@PathVariable String cacheName) {
    var cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("Cache not found: " + cacheName));
    }

    cache.clear();
    log.info("[CACHE] Cleared cache: {}", cacheName);
    return ResponseEntity.ok(ApiResponse.success("Cache cleared"));
  }

  @PostMapping("/clear-category/{category}")
  @Operation(summary = "Clear category", description = "Clear all caches in a category")
  public ResponseEntity<ApiResponse<Object>> clearCachesByCategory(@PathVariable String category) {
    var cleared = 0;

    for (String cacheName : cacheManager.getCacheNames()) {
      if (getCacheCategory(cacheName).equalsIgnoreCase(category)) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
          cache.clear();
          cleared++;
        }
      }
    }

    log.info("[CACHE] Cleared {} caches in category '{}'", cleared, category);
    return ResponseEntity.ok(ApiResponse.success("Cleared " + cleared + " caches"));
  }

  @PostMapping("/clear-all")
  @Operation(summary = "Clear all", description = "Clear all caches")
  public ResponseEntity<ApiResponse<Object>> clearAllCaches() {
    var count = 0;
    for (String cacheName : cacheManager.getCacheNames()) {
      var cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
        count++;
      }
    }

    log.warn("[CACHE] Cleared ALL {} caches", count);
    return ResponseEntity.ok(ApiResponse.success("Cleared all " + count + " caches"));
  }

  private String getCacheCategory(String cacheName) {
    if (cacheName.contains(":")) {
      var prefix = cacheName.substring(0, cacheName.indexOf(":"));
      return switch (prefix) {
        case "cfg" -> "Configuration";
        case "qsc" -> "QSC";
        case "sec" -> "Security";
        case "comic" -> "Comic";
        case "chapter" -> "Chapter";
        case "user" -> "User";
        case "ranking" -> "Ranking";
        case "search" -> "Search";
        case "comment" -> "Comment";
        case "img" -> "Image";
        case "api" -> "External API";
        default -> "Other";
      };
    }

    var name = cacheName.toLowerCase();
    if (name.contains("image")) return "Image";
    if (name.contains("settings")) return "Configuration";
    if (name.contains("user")) return "User";

    return "Other";
  }

  private String getDisplayName(String cacheName) {
    return cacheName.contains("#")
        ? cacheName.substring(0, cacheName.indexOf("#"))
        : cacheName;
  }

  private String parseDurationFromName(String cacheName) {
    return cacheName.contains("#")
        ? cacheName.substring(cacheName.indexOf("#") + 1)
        : "default";
  }

  @Builder
  record CacheStatsResponse(
      String cacheName, String displayName, String category,
      long hitCount, long missCount, double hitRate, String hitRatePercent,
      long evictionCount, long currentSize, String ttl
  ) {
  }
}

