package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Queue status information.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QueueStatus {

  private static final QueueStatus EMPTY = QueueStatus.builder()
      .totalItems(0)
      .pendingItems(0)
      .processingItems(0)
      .failedItems(0)
      .itemsByType(AggregatedStats.TypeCounts.builder()
          .category(0)
          .comic(0)
          .chapter(0)
          .image(0)
          .build())
      .build();

  int totalItems;
  int pendingItems;
  int processingItems;
  int failedItems;
  AggregatedStats.TypeCounts itemsByType;

  public static QueueStatus empty() {
    return EMPTY;
  }
}

