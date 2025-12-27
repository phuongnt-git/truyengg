package com.truyengg.model.dto;

/**
 * Queue statistics.
 */
public record QueueStats(
    long pending,
    long processing,
    long completed,
    long failed,
    long delayed
) {
  public long total() {
    return pending + processing + completed + failed + delayed;
  }
}
