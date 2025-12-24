package com.truyengg.model.dto;

import com.truyengg.domain.enums.ChapterCrawlStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record ChapterCrawlDto(
    Long id,
    Integer chapterIndex,
    String chapterUrl,
    String chapterName,
    ChapterCrawlStatus status,
    Integer totalImages,
    Integer downloadedImages,
    Long fileSizeBytes,
    String fileSizeFormatted,
    Long downloadTimeSeconds,
    String downloadTimeFormatted,
    Integer requestCount,
    Integer errorCount,
    BigDecimal downloadSpeedBytesPerSecond,
    String downloadSpeedFormatted,
    Integer retryCount,
    List<String> errorMessages,
    List<String> imagePaths,
    List<String> originalImagePaths,
    ZonedDateTime startedAt,
    ZonedDateTime completedAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
}

