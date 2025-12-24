package com.truyengg.model.dto;

public record ChapterCrawlRetryStatistics(
    Integer maxRetryCount,
    Double avgRetryCount,
    Long chaptersWithRetry,
    Long failedChaptersCount
) {
}
