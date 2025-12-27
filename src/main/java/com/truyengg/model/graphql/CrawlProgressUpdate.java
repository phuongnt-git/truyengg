package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * Progress update DTO for GraphQL subscriptions.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CrawlProgressUpdate {

  UUID jobId;
  int percent;
  int itemIndex;
  String itemName;
  int totalItems;
  int completedItems;
  int failedItems;
  long bytesDownloaded;
  String message;
  int estimatedRemainingSeconds;
}

