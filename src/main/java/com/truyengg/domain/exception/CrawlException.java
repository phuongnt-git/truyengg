package com.truyengg.domain.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * Unified exception for crawl job interruptions (pause/cancel).
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlException extends RuntimeException {

  UUID jobId;
  Reason reason;
  int lastIndex;

  private CrawlException(UUID jobId, Reason reason, int lastIndex, String message) {
    super(message);
    this.jobId = jobId;
    this.reason = reason;
    this.lastIndex = lastIndex;
  }

  public static CrawlException paused(UUID jobId, int lastIndex) {
    return new CrawlException(
        jobId,
        Reason.PAUSED,
        lastIndex,
        "Crawl job %s paused at item %d".formatted(jobId, lastIndex)
    );
  }

  public static CrawlException cancelled(UUID jobId) {
    return new CrawlException(
        jobId,
        Reason.CANCELLED,
        -1,
        "Crawl job %s was cancelled".formatted(jobId)
    );
  }

  public boolean isPaused() {
    return reason == Reason.PAUSED;
  }

  public boolean isCancelled() {
    return reason == Reason.CANCELLED;
  }

  public enum Reason {
    PAUSED,
    CANCELLED
  }
}
