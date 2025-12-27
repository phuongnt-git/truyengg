package com.truyengg.domain.enums;

/**
 * Unified crawl job status (simplified).
 * Merged PENDING+QUEUED into PENDING, removed PARTIAL.
 */
public enum CrawlStatus {
  /**
   * Created, waiting to be processed.
   */
  PENDING,

  /**
   * Currently processing.
   */
  RUNNING,

  /**
   * User paused, can resume.
   */
  PAUSED,

  /**
   * Successfully finished.
   */
  COMPLETED,

  /**
   * Error occurred, can retry.
   */
  FAILED,

  /**
   * User cancelled.
   */
  CANCELLED
}

