package com.truyengg.domain.enums;

/**
 * Types of errors that can occur during crawling.
 */
public enum CrawlErrorType {
  /**
   * Network-related error - auto retry.
   */
  NETWORK_ERROR,

  /**
   * Rate limited by source - delay then retry.
   */
  RATE_LIMITED,

  /**
   * Captcha required - skip, mark for manual retry.
   */
  CAPTCHA_REQUIRED,

  /**
   * Authentication required - skip, notify admin.
   */
  AUTH_REQUIRED,

  /**
   * Resource not found - skip permanently.
   */
  NOT_FOUND,

  /**
   * Parse/extraction error - log and skip.
   */
  PARSE_ERROR,

  /**
   * Timeout - auto retry with longer timeout.
   */
  TIMEOUT,

  /**
   * IP blocked - need proxy rotation.
   */
  BLOCKED
}

