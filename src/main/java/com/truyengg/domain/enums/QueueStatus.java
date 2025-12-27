package com.truyengg.domain.enums;

/**
 * Queue item status for crawl_queue table.
 */
public enum QueueStatus {
  /**
   * Waiting to be picked up.
   */
  PENDING,

  /**
   * Currently being processed.
   */
  PROCESSING,

  /**
   * Successfully processed.
   */
  COMPLETED,

  /**
   * Error occurred, may retry.
   */
  FAILED,

  /**
   * Skipped by user choice.
   */
  SKIPPED,

  /**
   * Waiting for delayed retry.
   */
  DELAYED
}

