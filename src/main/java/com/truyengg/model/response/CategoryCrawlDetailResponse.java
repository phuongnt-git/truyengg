package com.truyengg.model.response;

import com.truyengg.domain.entity.CategoryCrawlDetail;

import java.time.ZonedDateTime;

public record CategoryCrawlDetailResponse(
    Long id,
    String storyUrl,
    String storyTitle,
    String storySlug,
    Integer totalChapters,
    Integer crawledChapters,
    Integer failedChapters,
    String status,
    String errorMessage,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
  public static CategoryCrawlDetailResponse fromEntity(CategoryCrawlDetail detail) {
    return new CategoryCrawlDetailResponse(
        detail.getId(),
        detail.getStoryUrl(),
        detail.getStoryTitle(),
        detail.getStorySlug(),
        detail.getTotalChapters(),
        detail.getCrawledChapters(),
        detail.getFailedChapters(),
        detail.getStatus().name(),
        detail.getErrorMessage(),
        detail.getCreatedAt(),
        detail.getUpdatedAt()
    );
  }
}

