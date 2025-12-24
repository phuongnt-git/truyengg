package com.truyengg.model.response;

import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import com.truyengg.domain.enums.DownloadMode;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ComicCrawlResponse(
    UUID id,
    ComicCrawlStatus status,
    String url,
    DownloadMode downloadMode,
    List<Integer> downloadChapters,
    Integer partStart,
    Integer partEnd,
    Integer totalChapters,
    Integer downloadedChapters,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    String errorMessage,
    String createdByUsername,
    Long createdById,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
  public static ComicCrawlResponse fromEntity(ComicCrawl crawl, String username) {
    return new ComicCrawlResponse(
        crawl.getId(),
        crawl.getStatus(),
        crawl.getUrl(),
        crawl.getDownloadMode(),
        crawl.getDownloadChapters(),
        crawl.getPartStart(),
        crawl.getPartEnd(),
        crawl.getTotalChapters(),
        crawl.getDownloadedChapters(),
        crawl.getStartTime(),
        crawl.getEndTime(),
        crawl.getMessage(),
        username,
        crawl.getCreatedBy() != null ? crawl.getCreatedBy().getId() : null,
        crawl.getCreatedAt(),
        crawl.getUpdatedAt()
    );
  }
}

