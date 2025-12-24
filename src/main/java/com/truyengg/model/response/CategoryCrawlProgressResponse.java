package com.truyengg.model.response;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CategoryCrawlProgressResponse(
    UUID categoryCrawlJobId,
    String status,
    Integer currentPage,
    Integer currentStoryIndex,
    Integer totalStories,
    Integer crawledStories,
    Integer totalChapters,
    Integer crawledChapters,
    Integer totalImages,
    Integer downloadedImages,
    String currentMessage,
    List<String> messages,
    ZonedDateTime startTime,
    ZonedDateTime lastUpdate,
    Long elapsedSeconds,
    Map<String, CategoryCrawlDetailResponse> storyProgress
) {
}

