package com.truyengg.model.dto;

import com.truyengg.domain.enums.DuplicateType;

import java.util.UUID;

/**
 * Result of duplicate detection check.
 */
public record DuplicateCheckResult(
    DuplicateType type,
    UUID existingJobId,
    long existingContentId,
    int matchConfidence,
    String matchedUrl,
    int existingChapterCount
) {
  /**
   * Create a no-duplicate result.
   */
  public static DuplicateCheckResult noDuplicate() {
    return new DuplicateCheckResult(
        DuplicateType.NO_DUPLICATE, null, -1L, 0, null, 0
    );
  }

  /**
   * Create an exact URL match result.
   */
  public static DuplicateCheckResult exactUrl(UUID jobId, long contentId, String url, int chapterCount) {
    return new DuplicateCheckResult(
        DuplicateType.EXACT_URL, jobId, contentId, 100, url, chapterCount
    );
  }

  /**
   * Create a similar URL match result.
   */
  public static DuplicateCheckResult similarUrl(UUID jobId, long contentId, String url, int chapterCount) {
    return new DuplicateCheckResult(
        DuplicateType.SIMILAR_URL, jobId, contentId, 85, url, chapterCount
    );
  }

  /**
   * Create a content hash match result.
   */
  public static DuplicateCheckResult contentHash(UUID jobId, long contentId, String url, int chapterCount) {
    return new DuplicateCheckResult(
        DuplicateType.CONTENT_HASH, jobId, contentId, 90, url, chapterCount
    );
  }

  /**
   * Check if duplicate was found.
   */
  public boolean hasDuplicate() {
    return type != DuplicateType.NO_DUPLICATE;
  }

  /**
   * Check if it's an exact URL match.
   */
  public boolean isExactMatch() {
    return type == DuplicateType.EXACT_URL;
  }

  /**
   * Check if there's an active crawl for this content.
   */
  public boolean hasActiveCrawl() {
    return existingJobId != null;
  }
}

