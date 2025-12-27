package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Aggregated statistics for a crawl job and its descendants.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AggregatedStats {

  int totalChapters;
  int totalImages;
  long totalBytes;
  StatusCounts byStatus;
  TypeCounts byType;
  double avgProgress;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class StatusCounts {
    int pending;
    int running;
    int completed;
    int failed;
    int paused;
    int cancelled;
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class TypeCounts {
    int category;
    int comic;
    int chapter;
    int image;
  }
}

