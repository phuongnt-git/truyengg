package com.truyengg.model.response;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.enums.CrawlStatus;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.enums.DownloadMode;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Response DTO for crawl job.
 */
public record CrawlJobResponse(
    UUID id,
    CrawlType type,
    String targetUrl,
    String targetSlug,
    String targetName,
    CrawlStatus status,
    DownloadMode downloadMode,
    int depth,
    long contentId,
    int totalItems,
    int completedItems,
    int failedItems,
    int skippedItems,
    int percent,
    String errorMessage,
    int retryCount,
    UUID parentJobId,
    UUID rootJobId,
    boolean hasChildren,
    ZonedDateTime startedAt,
    ZonedDateTime completedAt,
    ZonedDateTime createdAt
) {
  /**
   * Create response from entity.
   */
  public static CrawlJobResponse from(CrawlJob job) {
    return new CrawlJobResponse(
        job.getId(),
        job.getCrawlType(),
        job.getTargetUrl(),
        job.getTargetSlug(),
        job.getTargetName(),
        job.getStatus(),
        job.getDownloadMode(),
        job.getDepth(),
        job.getContentId(),
        job.getTotalItems(),
        job.getCompletedItems(),
        job.getFailedItems(),
        job.getSkippedItems(),
        job.calculatePercent(),
        job.getErrorMessage(),
        job.getRetryCount(),
        job.getParentJob() != null ? job.getParentJob().getId() : null,
        job.getRootJob() != null ? job.getRootJob().getId() : null,
        job.getChildJobs() != null && !job.getChildJobs().isEmpty(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getCreatedAt()
    );
  }

  /**
   * Create response from entity with children flag.
   */
  public static CrawlJobResponse from(CrawlJob job, boolean hasChildren) {
    return new CrawlJobResponse(
        job.getId(),
        job.getCrawlType(),
        job.getTargetUrl(),
        job.getTargetSlug(),
        job.getTargetName(),
        job.getStatus(),
        job.getDownloadMode(),
        job.getDepth(),
        job.getContentId(),
        job.getTotalItems(),
        job.getCompletedItems(),
        job.getFailedItems(),
        job.getSkippedItems(),
        job.calculatePercent(),
        job.getErrorMessage(),
        job.getRetryCount(),
        job.getParentJob() != null ? job.getParentJob().getId() : null,
        job.getRootJob() != null ? job.getRootJob().getId() : null,
        hasChildren,
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getCreatedAt()
    );
  }
}

