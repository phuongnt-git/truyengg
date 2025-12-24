package com.truyengg.model.dto;

import java.math.BigDecimal;

public record ComicCrawlMetricsDto(
    Long totalFileSizeBytes,
    String totalFileSizeFormatted,
    Long totalDownloadTimeSeconds,
    String totalDownloadTimeFormatted,
    Integer totalRequestCount,
    Integer totalErrorCount,
    BigDecimal averageDownloadSpeedBytesPerSecond,
    String averageDownloadSpeedFormatted,
    Integer maxRetryCount,
    Double avgRetryCount,
    Long chaptersWithRetry,
    Long failedChaptersCount
) {
}

