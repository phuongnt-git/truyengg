package com.truyengg.model.dto;

import java.util.List;
import java.util.UUID;

import static java.lang.Math.max;

/**
 * DTO for crawl progress updates via WebSocket.
 */
public record CrawlProgressDto(
    UUID jobId,
    int itemIndex,
    String itemName,
    String itemUrl,
    int totalItems,
    int completedItems,
    int failedItems,
    int skippedItems,
    long bytesDownloaded,
    int percent,
    String message,
    List<String> messages,
    int estimatedRemainingSeconds
) {
  /**
   * Check if progress is complete.
   */
  public boolean isComplete() {
    return totalItems > 0 && (completedItems + failedItems + skippedItems) >= totalItems;
  }

  /**
   * Get remaining items count.
   */
  public int getRemainingItems() {
    return max(0, totalItems - completedItems - failedItems - skippedItems);
  }

  /**
   * Get processed items count.
   */
  public int getProcessedItems() {
    return completedItems + failedItems + skippedItems;
  }
}
