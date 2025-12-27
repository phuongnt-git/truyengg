package com.truyengg.model.response;

import java.util.Map;

/**
 * Response DTO for crawl statistics.
 */
public record CrawlStatsResponse(
    long totalJobs,
    long activeJobs,
    long completedJobs,
    long failedJobs,
    Map<String, Long> jobsByType,
    Map<String, Long> jobsByStatus
) {
}

