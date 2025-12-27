package com.truyengg.domain.enums;

/**
 * Actions to take when an error occurs during crawling.
 */
public enum ErrorAction {
  /**
   * Retry immediately.
   */
  RETRY_IMMEDIATE,

  /**
   * Retry after a delay (exponential backoff).
   */
  RETRY_DELAYED,

  /**
   * Skip and mark for manual retry later.
   */
  SKIP_MARK_MANUAL,

  /**
   * Skip and notify admin.
   */
  SKIP_NOTIFY_ADMIN,

  /**
   * Skip permanently (e.g., 404).
   */
  SKIP_PERMANENT,

  /**
   * Skip and log the error.
   */
  SKIP_LOG
}

