package com.truyengg.model.response;

import com.truyengg.domain.entity.CategoryCrawlJob;

import java.time.ZonedDateTime;
import java.util.UUID;

public record CategoryCrawlJobResponse(
    UUID id,
    String categoryUrl,
    String source,
    String status,
    Integer totalPages,
    Integer crawledPages,
    Integer totalStories,
    Integer crawledStories,
    Long createdBy,
    String createdByUsername,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
  public static CategoryCrawlJobResponse fromEntity(CategoryCrawlJob job, String username) {
    return new CategoryCrawlJobResponse(
        job.getId(),
        job.getCategoryUrl(),
        job.getSource(),
        job.getStatus().name(),
        job.getTotalPages(),
        job.getCrawledPages(),
        job.getTotalStories(),
        job.getCrawledStories(),
        job.getCreatedBy().getId(),
        username,
        job.getCreatedAt(),
        job.getUpdatedAt()
    );
  }
}

