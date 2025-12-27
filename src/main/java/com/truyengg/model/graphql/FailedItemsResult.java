package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Result containing failed items for retry management.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FailedItemsResult {

  int totalCount;
  List<FailedItem> items;

  public static FailedItemsResult empty() {
    return FailedItemsResult.builder()
        .totalCount(0)
        .items(emptyList())
        .build();
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class FailedItem {
    int index;
    String url;
    String name;
    String error;
    int retryCount;
  }
}

