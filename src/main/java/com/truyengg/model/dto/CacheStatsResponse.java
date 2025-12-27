package com.truyengg.model.dto;

import lombok.Builder;

@Builder
public record CacheStatsResponse(
    String cacheName, String displayName, String category,
    long hitCount, long missCount, double hitRate, String hitRatePercent,
    long evictionCount, long currentSize, String ttl
) {
}
