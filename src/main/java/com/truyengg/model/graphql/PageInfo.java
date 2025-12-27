package com.truyengg.model.graphql;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Relay-style page info for cursor-based pagination.
 */
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageInfo {

  boolean hasNextPage;
  boolean hasPreviousPage;
  String startCursor;
  String endCursor;
}

