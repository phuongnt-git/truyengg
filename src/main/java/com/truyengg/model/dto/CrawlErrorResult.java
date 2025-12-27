package com.truyengg.model.dto;

import com.truyengg.domain.enums.ErrorAction;

/**
 * Result of error handling decision.
 */
public record CrawlErrorResult(
    ErrorAction action,
    long delayMs,
    boolean shouldSkip
) {
  /**
   * Check if should retry.
   */
  public boolean shouldRetry() {
    return action == ErrorAction.RETRY_IMMEDIATE || action == ErrorAction.RETRY_DELAYED;
  }

  /**
   * Check if retry is delayed.
   */
  public boolean isDelayed() {
    return action == ErrorAction.RETRY_DELAYED && delayMs > 0;
  }
}

