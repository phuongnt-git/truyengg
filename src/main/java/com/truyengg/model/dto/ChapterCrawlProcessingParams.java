package com.truyengg.model.dto;

import com.truyengg.domain.entity.Comic;
import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parameters for chapter crawl processing.
 */
@Builder
public record ChapterCrawlProcessingParams(
    String url,
    String domain,
    UUID crawlId,
    String chapterKey,
    int chapterIndex,
    Map<String, Object> chapterProgress,
    List<String> messages,
    ZonedDateTime startTime,
    int totalChapters,
    int currentTotalImages,
    int currentTotalDownloadedImages,
    Comic comic,
    int resumeFromImageIndex,
    List<String> resumeImageUrls
) {
}
